package com.footpredict.local.presentation.screen.matchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.footpredict.local.data.local.LocalDataSource
import com.footpredict.local.data.mapper.EntityMappers.toDomain
import com.footpredict.local.data.mapper.EntityMappers.toEntity
import com.footpredict.local.data.mapper.EntityMappers.toRecentMatch
import com.footpredict.local.domain.model.AlertSeverity
import com.footpredict.local.domain.model.AlertType
import com.footpredict.local.domain.model.BettingAlert
import com.footpredict.local.domain.model.EnrichedMatch
import com.footpredict.local.domain.model.Odds
import dagger.hilt.android.qualifiers.ApplicationContext
import com.footpredict.local.domain.model.LivePredictionHistory
import com.footpredict.local.domain.model.LivePredictionSnapshot
import com.footpredict.local.domain.model.Resource
import com.footpredict.local.domain.repository.MatchRepository
import com.footpredict.local.domain.usecase.CalculateDixonColesUseCase
import com.footpredict.local.domain.usecase.CalculateLiveRestOfMatchUseCase
import com.footpredict.local.domain.usecase.ComputePerformanceStatsUseCase
import com.footpredict.local.domain.usecase.EloRatingUseCase
import com.footpredict.local.domain.usecase.EnsemblePredictionUseCase
import com.footpredict.local.domain.usecase.GenerateBettingAlertsUseCase
import com.footpredict.local.domain.usecase.MarketModelUseCase
import com.footpredict.local.domain.usecase.ValidateLiveMatchesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import com.footpredict.local.presentation.screen.matchlist.topDoubleChance
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class MatchPredictionViewModel @Inject constructor(
    private val repository: MatchRepository,
    private val local: LocalDataSource,
    private val dixonColes: CalculateDixonColesUseCase,
    private val liveRestOfMatch: CalculateLiveRestOfMatchUseCase,
    private val marketModel: MarketModelUseCase,
    private val eloModel: EloRatingUseCase,
    private val ensemble: EnsemblePredictionUseCase,
    private val generateAlerts: GenerateBettingAlertsUseCase,
    private val computePerformance: ComputePerformanceStatsUseCase,
    private val notifier: com.footpredict.local.data.notification.AlertNotifier,
    @ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(MatchUiState())
    val uiState: StateFlow<MatchUiState> = _uiState.asStateFlow()

    private var season = 2026
    private var livePollJob: Job? = null
    private var allMatches: List<EnrichedMatch> = emptyList()
    /** Ligues chargées (toutes par défaut : pas de filtre de ligue imposé). */
    private val loadedLeagues = mutableSetOf<Int>()
    /** Sérialise les calculs de pronostics (rechargements multi-ligues). */
    private val computeMutex = Mutex()
    /** Dernières cotes vues (détection de chute > 10 % en session). */
    private var prevOdds: Map<Int, com.footpredict.local.domain.model.Odds> = emptyMap()

    companion object {
        private const val LIVE_REFRESH_INTERVAL_MS = 30_000L
        private val LIVE_STATUSES = setOf("1H", "HT", "2H", "ET", "BT", "P", "LIVE", "INT")
        private val PREMATCH_STATUSES = setOf("NS", "TBD", "PST")
        private const val RECENT_LIMIT = 10
        /** Ligues chargées d’office (registre `ApiFootballLeagues`, quota 100/j). */
        val LEAGUES = com.footpredict.local.data.remote.fixture.ApiFootballLeagues.AUTO_IDS
        /**
         * Borne de calcul par défaut (opt-in non validé) : pronostics sur les
         * 30 prochains jours max (protège CPU/batterie sur des milliers de
         * matchs multi-ligues ; l’affichage, lui, n’est jamais borné).
         */
        private const val COMPUTE_UPCOMING_DAYS = 30
        private const val PREFS_FILTERS = "filter_settings"
    }

    init {
        _uiState.update { it.copy(windows = loadWindows()) }
        loadAllLeagues()
    }

    private fun prefs() =
        appContext.getSharedPreferences(PREFS_FILTERS, android.content.Context.MODE_PRIVATE)

    private fun loadWindows(): FilterWindows {
        val p = prefs()
        return FilterWindows.sanitize(
            p.getInt("upcoming_days", FilterWindows.DEFAULT_UPCOMING_DAYS),
            p.getInt("past_days", FilterWindows.DEFAULT_PAST_DAYS)
        )
    }

    private fun saveWindows(w: FilterWindows) {
        prefs().edit()
            .putInt("upcoming_days", w.upcomingDays)
            .putInt("past_days", w.pastDays)
            .apply()
    }

    /**
     * Changement de ligue SANS rechargement si déjà chargée, sinon chargement
     * à la demande (exigences quota : 1–3 req, puis cache 6 h). Le filtre
     * s’applique dans tous les cas aux données locales.
     */
    fun selectLeague(newLeagueId: Int) {
        _uiState.update { it.copy(leagueId = newLeagueId) }
        if (newLeagueId != ALL_LEAGUES && newLeagueId !in loadedLeagues) {
            ensureLeague(newLeagueId)
        } else {
            applyFilters()
        }
    }

    /** Charge UNE ligue à la demande (sélection explicite hors lot auto). */
    private fun ensureLeague(leagueId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                var last: Resource<List<EnrichedMatch>>? = null
                repository.getEnrichedMatches(leagueId, season).collect { last = it }
                val res = last
                if (res is Resource.Success) {
                    loadedLeagues += leagueId
                    allMatches = (allMatches.filter { it.fixture.leagueId != leagueId } + res.data)
                        .distinctBy { it.fixture.id }
                    applyFilters()
                    _uiState.update {
                        it.copy(isLoading = false, isFromCache = res.fromCache, isOffline = false)
                    }
                    computePredictionsAndAlerts(allMatches)
                    startLivePollingIfNeeded(allMatches)
                    if (!res.fromCache) scheduleTrigger()
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Ligue indisponible hors-ligne")
                    }
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Ligue indisponible hors-ligne")
                }
            }
        }
    }

    /**
     * Validation simultanée du panneau de filtres : ligue, recherche, statut,
     * confiance min, intervalle de dates, fenêtres globales (persistées).
     * Un seul point d’application → listes, tops et alertes cohérents.
     * C’EST ICI, et seulement ici, que les filtres deviennent actifs
     * ([filtersApplied] : par défaut, l’écran Matchs montre TOUT).
     */
    fun applyFilterBundle(
        leagueId: Int,
        query: String,
        statusFilter: StatusFilter,
        minConfidence: Float,
        dateRangeMs: Pair<Long, Long>?,
        upcomingDays: Int,
        pastDays: Int
    ) {
        val windows = FilterWindows.sanitize(upcomingDays, pastDays)
        saveWindows(windows)
        _uiState.update {
            it.copy(
                leagueId = leagueId,
                query = query,
                statusFilter = statusFilter,
                minConfidence = minConfidence.coerceIn(0f, 90f),
                dateRangeMs = dateRangeMs,
                windows = windows,
                filtersApplied = true
            )
        }
        // Ligue jamais chargée (hors lot auto) → chargement à la demande
        // (quota) avant d’appliquer ; sinon filtre local immédiat.
        if (leagueId != ALL_LEAGUES && leagueId !in loadedLeagues) {
            ensureLeague(leagueId)
        } else {
            applyFilters()
            computePredictionsAndAlerts(allMatches)
        }
    }

    /**
     * Réinitialisation : tout réafficher (ligue « toutes », filtres inactifs).
     * Les fenêtres persistées sont conservées pour la prochaine validation.
     */
    fun resetFilters() {
        _uiState.update {
            it.copy(
                leagueId = ALL_LEAGUES,
                query = "",
                statusFilter = StatusFilter.ALL,
                minConfidence = 0f,
                dateRangeMs = null,
                filtersApplied = false
            )
        }
        applyFilters()
        computePredictionsAndAlerts(allMatches)
    }

    fun setMinConfidence(percent: Float) {
        _uiState.update { it.copy(minConfidence = percent.coerceIn(0f, 90f)) }
        applyFilters()
    }

    /**
     * Vue filtrée + triée : matchs ouverts (live + à venir) par date croissante
     * d’abord, puis terminés par date décroissante.
     *
     * Opt-in strict : tant que l’utilisateur n’a pas validé ([filtersApplied]
     * faux), seule la ligue choisie s’applique (« toutes » par défaut, donc
     * rien) — ni fenêtre de dates, ni recherche, ni confiance.
     */
    private fun applyFilters() {
        val s = _uiState.value
        val q = s.query.trim().lowercase()
        val nowMs = System.currentTimeMillis()
        val filtered = allMatches.filter { em ->
            val leagueOk = s.leagueId == ALL_LEAGUES || em.fixture.leagueId == s.leagueId
            val statusOk = when (s.statusFilter) {
                StatusFilter.ALL -> true
                StatusFilter.UPCOMING -> em.fixture.statusShort != "FT"
                StatusFilter.FINISHED -> em.fixture.statusShort == "FT"
            }
            val queryOk = q.isEmpty() ||
                em.fixture.homeTeamName.lowercase().contains(q) ||
                em.fixture.awayTeamName.lowercase().contains(q)
            val confOk = s.minConfidence <= 0f ||
                ((s.predictions[em.fixture.id]?.confidence ?: -1.0) * 100).toFloat() >= s.minConfidence
            val dateOk = !s.filtersApplied || PickHelpers.inDateWindow(
                em.fixture.dateTimestamp, nowMs,
                s.windows.upcomingDays, s.windows.pastDays, s.dateRangeMs
            )
            leagueOk && statusOk && queryOk && confOk && dateOk
        }
        val (open, closed) = filtered.partition { it.fixture.statusShort != "FT" }
        _uiState.update {
            it.copy(
                matches = open.sortedBy { m -> m.fixture.dateTimestamp } +
                    closed.sortedByDescending { m -> m.fixture.dateTimestamp },
                finishedMatches = allMatches
                    .filter { m -> m.fixture.statusShort == "FT" }
                    .filter { m ->
                        val qq = q
                        (s.leagueId == ALL_LEAGUES || m.fixture.leagueId == s.leagueId) &&
                        (qq.isEmpty() || m.fixture.homeTeamName.lowercase().contains(qq) ||
                            m.fixture.awayTeamName.lowercase().contains(qq)) &&
                            (!s.filtersApplied || PickHelpers.inDateWindow(
                                m.fixture.dateTimestamp, nowMs,
                                s.windows.upcomingDays, s.windows.pastDays, s.dateRangeMs
                            ))
                    }
                    .sortedByDescending { it.fixture.dateTimestamp }
            )
        }
    }

    /**
     * Chargement initial : TOUTES les ligues en parallèle (pas de filtre de
     * ligue imposé — exigence « Matchs non filtré par défaut »). Chaque ligue
     * profite de son cache 6 h (quota préservé) ; une ligue en échec n’empêche
     * pas les autres (dégradation gracieuse). Rafraîchit via [retry].
     */
    fun loadAllLeagues() {
        stopLivePolling()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val perLeague = LEAGUES.map { lid ->
                async {
                    try {
                        var last: Resource<List<EnrichedMatch>>? = null
                        repository.getEnrichedMatches(lid, season).collect { last = it }
                        lid to last
                    } catch (_: Exception) {
                        lid to null
                    }
                }
            }.awaitAll()
            var anyData = allMatches.isNotEmpty()
            var anyFresh = false
            for ((lid, res) in perLeague) {
                if (res is Resource.Success) {
                    anyData = true
                    if (!res.fromCache) anyFresh = true
                    loadedLeagues += lid
                    allMatches = (allMatches.filter { it.fixture.leagueId != lid } + res.data)
                        .distinctBy { it.fixture.id }
                }
            }
            applyFilters()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isFromCache = !anyFresh,
                    isOffline = !anyData,
                    errorMessage = if (!anyData) "Aucune source disponible" else null
                )
            }
            if (anyData) {
                computePredictionsAndAlerts(allMatches)
                startLivePollingIfNeeded(allMatches)
                if (anyFresh) scheduleTrigger()
            }
        }
    }

    fun setLiveAutoRefresh(enabled: Boolean) {
        _uiState.update { it.copy(liveAutoRefreshEnabled = enabled) }
        if (enabled) startLivePollingIfNeeded(_uiState.value.matches)
        else stopLivePolling()
    }

    fun refreshLiveNow() {
        viewModelScope.launch { pollLiveOnce() }
    }

    private fun startLivePollingIfNeeded(matches: List<EnrichedMatch>) {
        // Guet élargi (synchro live) : matchs en cours + coups d’envoi
        // imminents/récents. Sans guet, un coup d’envoi passerait inaperçu
        // jusqu’à la synchro 6 h (ancien bug : polling coupé sans live).
        if (!isLiveWatched(matches) || !_uiState.value.liveAutoRefreshEnabled) {
            stopLivePolling()
            return
        }
        if (livePollJob?.isActive == true) return
        livePollJob = viewModelScope.launch {
            while (isActive && _uiState.value.liveAutoRefreshEnabled) {
                delay(LIVE_REFRESH_INTERVAL_MS)
                pollLiveOnce()
            }
        }
    }

    private fun stopLivePolling() {
        livePollJob?.cancel()
        livePollJob = null
        _uiState.update { it.copy(isLiveRefreshing = false) }
    }

    private suspend fun pollLiveOnce() {
        if (!_uiState.value.liveAutoRefreshEnabled) return
        _uiState.update { it.copy(isLiveRefreshing = true) }
        try {
            // Synchro live ciblée (ligues sous guet uniquement : quota).
            var flipped = 0
            for (lid in watchedLeagueIds()) {
                flipped += repository.validateLiveMatches(lid, season)
            }
            if (flipped > 0) {
                loadAllLeagues()
            } else {
                _uiState.update { it.copy(lastLiveRefreshAt = System.currentTimeMillis()) }
                if (!isLiveWatched()) {
                    stopLivePolling()
                }
            }
        } catch (_: Exception) {
        } finally {
            _uiState.update { it.copy(isLiveRefreshing = false) }
        }
    }

    /** Vrai si un match mérite encore le guet (en cours ou coup d’envoi proche). */
    private fun isLiveWatched(matches: List<EnrichedMatch> = allMatches): Boolean {
        val nowMs = System.currentTimeMillis()
        return matches.any {
            val elapsedMin = (nowMs - it.fixture.dateTimestamp * 1000) / 60_000L
            ValidateLiveMatchesUseCase.warrantsWatch(it.fixture.statusShort, elapsedMin)
        }
    }

    /** Ligues avec au moins un match sous guet (polling ciblé, quota FD). */
    private fun watchedLeagueIds(): Set<Int> {
        val nowMs = System.currentTimeMillis()
        return allMatches.filter {
            val elapsedMin = (nowMs - it.fixture.dateTimestamp * 1000) / 60_000L
            ValidateLiveMatchesUseCase.warrantsWatch(it.fixture.statusShort, elapsedMin)
        }.map { it.fixture.leagueId }.toSet()
    }

    private fun computePredictionsAndAlerts(
        matches: List<EnrichedMatch>,
        includeLive: Boolean = false
    ) {
        viewModelScope.launch {
            // Rechargements multi-ligues concurrents : sérialisés, et la carte
            // accumule (pas de perte entre deux passes).
            computeMutex.withLock {
            val map = _uiState.value.predictions.toMutableMap()

            // ——— Prematch : vivier borné (30 j par défaut non validé : protège
            // CPU/batterie ; fenêtres utilisateur après validation explicite).
            val state = _uiState.value
            val windowNow = System.currentTimeMillis()
            val activeUpcoming =
                if (state.filtersApplied) state.windows.upcomingDays else COMPUTE_UPCOMING_DAYS
            val activePast =
                if (state.filtersApplied) state.windows.pastDays else COMPUTE_UPCOMING_DAYS
            val upcomingPool = PickHelpers.allUpcoming(
                matches.filter { it.fixture.statusShort in PREMATCH_STATUSES }
            ).filter {
                PickHelpers.inDateWindow(
                    it.fixture.dateTimestamp, windowNow,
                    activeUpcoming, activePast, state.dateRangeMs
                )
            }
            val detailedIds = upcomingPool.map { it.fixture.id }.toSet()
            val liveIds = mutableSetOf<Int>()
            // Vague 1 : chaque carte se remplit dès son calcul (jamais de freeze).
            _uiState.update { it.copy(computeProgress = 0 to upcomingPool.size) }
            upcomingPool.forEachIndexed { index, em ->
                val nowMs = System.currentTimeMillis()
                val homeRecent = try {
                    local.getLastFinishedForTeam(em.fixture.homeTeamId, RECENT_LIMIT)
                        .mapNotNull { it.toRecentMatch(em.fixture.homeTeamId, nowMs) }
                } catch (_: Exception) {
                    emptyList()
                }
                val awayRecent = try {
                    local.getLastFinishedForTeam(em.fixture.awayTeamId, RECENT_LIMIT)
                        .mapNotNull { it.toRecentMatch(em.fixture.awayTeamId, nowMs) }
                } catch (_: Exception) {
                    emptyList()
                }
                val dc = if (em.homeStats != null && em.awayStats != null) {
                    dixonColes(
                        em.fixture, em.homeStats, em.awayStats,
                        homeRecent = homeRecent, awayRecent = awayRecent
                    )
                } else null
                val market = em.odds?.let { marketModel(it) }
                val elo = eloModel(em.fixture, em.eloHome ?: 1500.0, em.eloAway ?: 1500.0)
                map[em.fixture.id] = ensemble(em.fixture.id, dc, market, elo)
                // Progression clairsemée (audit ANR : pas de recomposition à chaque carte).
                if (index % 20 == 0 || index == upcomingPool.lastIndex) {
                    val snapshot = map.toMap()
                    _uiState.update {
                        it.copy(
                            predictions = snapshot,
                            computeProgress = (index + 1) to upcomingPool.size
                        )
                    }
                }
            }

            // ——— Live : Rest-of-Match ———
            if (includeLive || matches.any { it.fixture.statusShort in LIVE_STATUSES }) {
                matches.filter { it.fixture.statusShort in LIVE_STATUSES }.forEach { em ->
                    val gH = em.fixture.goalsHome ?: 0
                    val gA = em.fixture.goalsAway ?: 0

                    // λ pré-match (recalcul ou défaut neutre)
                    val prematchDc = if (em.homeStats != null && em.awayStats != null) {
                        dixonColes(em.fixture, em.homeStats, em.awayStats)
                    } else null

                    val lambdaH = prematchDc?.lambdaHome ?: 1.35
                    val lambdaA = prematchDc?.lambdaAway ?: 1.15

                    val liveRom = liveRestOfMatch(
                        matchId = em.fixture.id,
                        lambdaHome = lambdaH,
                        lambdaAway = lambdaA,
                        goalsHome = gH,
                        goalsAway = gA,
                        elapsedMinutes = null,
                        statusShort = em.fixture.statusShort
                    )

                    val market = em.odds?.let { marketModel(it) }
                    val liveEnsemble = liveRestOfMatch.toEnsemble(
                        matchId = em.fixture.id,
                        liveRom = liveRom,
                        marketLive = market,
                        prematchConfidence = prematchDc?.let {
                            maxOf(it.homeWinProb, it.drawProb, it.awayWinProb)
                        } ?: 0.4
                    )
                    map[em.fixture.id] = liveEnsemble
                    liveIds += em.fixture.id
                }
            }

            // Snapshots évolution
            val histories = _uiState.value.liveHistories.toMutableMap()
            matches.filter { it.fixture.statusShort in LIVE_STATUSES }.forEach { em ->
                val pred = map[em.fixture.id] ?: return@forEach
                val snap = LivePredictionSnapshot(
                    matchId = em.fixture.id,
                    elapsed = null,
                    statusShort = em.fixture.statusShort,
                    goalsHome = em.fixture.goalsHome,
                    goalsAway = em.fixture.goalsAway,
                    homeWinProb = pred.homeWinProb,
                    drawProb = pred.drawProb,
                    awayWinProb = pred.awayWinProb,
                    confidence = pred.confidence
                )
                val prev = histories[em.fixture.id]?.snapshots.orEmpty()
                val lastTs = prev.lastOrNull()?.timestamp ?: 0L
                if (System.currentTimeMillis() - lastTs > 20_000) {
                    histories[em.fixture.id] = LivePredictionHistory(
                        matchId = em.fixture.id,
                        snapshots = (prev + snap).takeLast(30)
                    )
                }
            }

            val alerts = (dropAlerts(matches) + generateAlerts(matches, map)).take(12)
            val tops = PickHelpers.topConfident(upcomingPool, map, 3)
            settleFinished(matches)
            val avoid = PickHelpers.toAvoid(
                upcomingPool, map, exclude = tops.map { it.matchId }.toSet(), n = 3
            )
            // Cartes détaillées : tous les matchs à venir + live.
            // Purge des pronostics périmés (fenêtre resserrée après validation).
            val detailed = map.filterKeys { it in detailedIds || it in liveIds }
            map.keys.retainAll(detailed.keys)
            // Coupon du jour (v1.3.2, accueil) : jambes strictes — confiance
            // modèle ≥ 75 % ET value bet active (edge > 0 exigé dans
            // buildCoupon). Sinon null honnête + message capital à l’accueil.
            val couponPool = upcomingPool.filter {
                (map[it.fixture.id]?.confidence ?: 0.0) >= 0.75
            }
            val coupon = buildCoupon(
                couponPool.filter { it.fixture.id in detailedIds },
                detailed
            )
            // Safe du jour (v1.3.2) : lignes dégradées à probabilité ≥ 88 %,
            // sans condition de confiance modèle (la ligne sécurisée porte
            // déjà sa propre sécurité). Null honnête si vide.
            val safeCoupon = buildSafeCoupon(
                upcomingPool.filter { it.fixture.id in detailedIds },
                map
            )
            notifier.notifyHigh(alerts)
            _uiState.update {
                it.copy(
                    predictions = detailed,
                    liveHistories = histories,
                    alerts = alerts,
                    topPicks = tops,
                    avoidPicks = avoid,
                    topOver = PickHelpers.topOver25(upcomingPool, map),
                    topUnder = PickHelpers.topUnder25(upcomingPool, map),
                    topBttsYes = PickHelpers.topBttsYes(upcomingPool, map),
                    topBttsNo = PickHelpers.topBttsNo(upcomingPool, map),
                    topDC = topDoubleChance(upcomingPool, map),
                    coupon = coupon,
                    safeCoupon = safeCoupon,
                    computeProgress = null,
                    showNotifPrompt = alerts.any { a ->
                        a.severity == com.footpredict.local.domain.model.AlertSeverity.HIGH
                    } && !notifier.hasPermission()
                )
            }
            } // computeMutex
        }
    }

    /**
     * Évaluation rétrospective des matchs terminés (pipeline settled).
     *
     * Pour chaque FT NON DÉJÀ SOLDÉ : prédiction recalculée avec les données
     * STRICTEMENT antérieures au coup de sifflet (forme récente filtrée par
     * date ; marché exclu car pas d’historique de cotes), comparée au score réel.
     * Approximation documentée : l’Elo utilisé est le rating courant, pas
     * celui d’avant-match. Ne fait jamais échouer l’affichage.
     *
     * v1.3.1 (audit externe 2026-10-14) :
     * - IMMUABILITÉ : un match déjà soldé n’est jamais recalculé (le flip
     *   GAGNÉ→PERDU du 560574 entre deux exports est interdit : un pari jugé
     *   ne se rejoue pas).
     * - MARCHÉ PROPOSÉ : [wasHit] valide le marché proposé par
     *   [bestMarketPick] (même fonction que l’export), pas le 1X2 seul
     *   (un « BTTS - No » sur 5-3 est PERDU même si le 1X2 était bon).
     * - COTE FIGÉE : la meilleure cote du moment est mémorisée ([bookmakerOdds])
     *   pour la simulation (l’export ne dépend plus des cotes courantes).
     */
    private fun settleFinished(matches: List<EnrichedMatch>) {
        viewModelScope.launch {
            try {
                val alreadySettled = try {
                    local.getAllSettled().map { it.matchId }.toSet()
                } catch (_: Exception) {
                    emptySet()
                }
                // Migration sémantique UNIQUE (audit externe 2026-10-14) —
                // PLACÉE AVANT TOUTE SORTIE : elle doit tourner même quand
                // aucun nouveau terminé n’existe (sinon l’historique garde
                // l’ancienne sémantique 1X2). Lignes soldées AVANT le jugement
                // au marché (predictedMarket null) recorrigées UNE FOIS avec
                // leurs probabilités FIGÉES (aucun recalcul modèle, donc aucun
                // re-flip possible ensuite). Scores lus sur TOUS les FT.
                try {
                    val ftScores = matches.filter {
                        it.fixture.statusShort == "FT" &&
                            it.fixture.goalsHome != null && it.fixture.goalsAway != null
                    }.associate {
                        it.fixture.id to (it.fixture.goalsHome!! to it.fixture.goalsAway!!)
                    }
                    val legacy = local.getAllSettled().map { it.toDomain() }
                        .filter { it.predictedMarket == null && ftScores.containsKey(it.matchId) }
                        .map { sp ->
                            val (gh, ga) = ftScores.getValue(sp.matchId)
                            val best = com.footpredict.local.domain.model.bestMarketPick(
                                sp.homeWinProb, sp.drawProb, sp.awayWinProb,
                                sp.over25Prob, sp.bttsYesProb
                            )
                            sp.copy(
                                wasHit = com.footpredict.local.domain.model.marketHit(best.kind, gh, ga),
                                predictedMarket = best.kind.name
                            ).toEntity()
                        }
                    if (legacy.isNotEmpty()) local.upsertSettledAll(legacy)
                } catch (_: Exception) {
                }
                val finished = matches.filter {
                    it.fixture.statusShort == "FT" &&
                        it.fixture.goalsHome != null && it.fixture.goalsAway != null &&
                        it.homeStats != null && it.awayStats != null &&
                        it.fixture.id !in alreadySettled
                }
                if (finished.isEmpty()) {
                    refreshPerformance()
                    return@launch
                }
                val settled = finished.mapNotNull { em ->
                    val gh = em.fixture.goalsHome ?: return@mapNotNull null
                    val ga = em.fixture.goalsAway ?: return@mapNotNull null
                    val cutoff = em.fixture.dateTimestamp * 1000
                    val homeRecent = try {
                        local.getLastFinishedForTeam(em.fixture.homeTeamId, RECENT_LIMIT)
                    } catch (_: Exception) {
                        emptyList()
                    }.filter { it.dateTimestamp * 1000 < cutoff }
                        .take(RECENT_LIMIT)
                        .mapNotNull { it.toRecentMatch(em.fixture.homeTeamId, cutoff) }
                    val awayRecent = try {
                        local.getLastFinishedForTeam(em.fixture.awayTeamId, RECENT_LIMIT)
                    } catch (_: Exception) {
                        emptyList()
                    }.filter { it.dateTimestamp * 1000 < cutoff }
                        .take(RECENT_LIMIT)
                        .mapNotNull { it.toRecentMatch(em.fixture.awayTeamId, cutoff) }
                    val dc = dixonColes(
                        em.fixture, em.homeStats!!, em.awayStats!!,
                        homeRecent = homeRecent, awayRecent = awayRecent
                    )
                    val elo = eloModel(em.fixture, em.eloHome ?: 1500.0, em.eloAway ?: 1500.0)
                    val ens = ensemble(em.fixture.id, dc, null, elo)
                    val predicted = when {
                        ens.homeWinProb >= ens.drawProb && ens.homeWinProb >= ens.awayWinProb -> "HOME"
                        ens.awayWinProb >= ens.drawProb -> "AWAY"
                        else -> "DRAW"
                    }
                    val actual = when {
                        gh > ga -> "HOME"
                        gh < ga -> "AWAY"
                        else -> "DRAW"
                    }
                    // Marché proposé (même fonction que carte + export) et
                    // statut jugé SUR CE MARCHÉ (audit 2026-10-14).
                    val best = com.footpredict.local.domain.model.bestMarketPick(
                        ens.homeWinProb, ens.drawProb, ens.awayWinProb,
                        ens.over25Prob, ens.bttsYesProb
                    )
                    val frozenOdds = listOfNotNull(
                        em.odds?.homeOdds, em.odds?.drawOdds, em.odds?.awayOdds
                    ).maxOrNull()
                    com.footpredict.local.domain.model.SettledPrediction(
                        matchId = em.fixture.id,
                        predictedOutcome = predicted,
                        actualOutcome = actual,
                        homeWinProb = ens.homeWinProb,
                        drawProb = ens.drawProb,
                        awayWinProb = ens.awayWinProb,
                        confidence = ens.confidence,
                        wasHit = com.footpredict.local.domain.model.marketHit(best.kind, gh, ga),
                        edgeOnPredicted = null,
                        settledAt = System.currentTimeMillis(),
                        over25Prob = ens.over25Prob,
                        bttsYesProb = ens.bttsYesProb,
                        predictedMarket = best.kind.name,
                        bookmakerOdds = frozenOdds
                    ).toEntity()
                }
                if (settled.isNotEmpty()) local.upsertSettledAll(settled)
                refreshPerformance()
            } catch (_: Exception) {
                // Évaluation best-effort.
            }
        }
    }

    private suspend fun refreshPerformance() {
        try {
            val settled = local.getAllSettled().map { it.toDomain() }
            val pending = _uiState.value.predictions.keys.count { id ->
                allMatches.firstOrNull { it.fixture.id == id }
                    ?.fixture?.statusShort != "FT"
            }
            _uiState.update {
                it.copy(
                    performance = computePerformance(settled),
                    summary = buildSummary(settled, pending),
                    settledMap = settled.associateBy { s -> s.matchId }
                )
            }
        } catch (_: Exception) {
        }
    }

    fun retry() = loadAllLeagues()

    /**
     * Déclencheur push après synchro réussie : le Worker réévalue en fond
     * (Top 5 + conf ≥ 70 % + value ≥ 15 %) et notifie via AlertNotifier
     * (plafond 2/jour, opt-in requis).
     */
    /**
     * Déclencheur push après synchro réussie (une seule ligue : la PL si
     * chargée, sinon la première — le plafond 2/jour de l’AlertNotifier est
     * global, on n’empile pas un worker par ligue).
     */
    private fun scheduleTrigger() {
        val triggerLeague = if (39 in loadedLeagues) 39 else loadedLeagues.firstOrNull() ?: return
        try {
            val request = androidx.work.OneTimeWorkRequestBuilder<com.footpredict.local.data.notification.PredictionTriggerWorker>()
                .setInputData(
                    androidx.work.workDataOf(
                        com.footpredict.local.data.notification.PredictionTriggerWorker.KEY_LEAGUE to triggerLeague,
                        com.footpredict.local.data.notification.PredictionTriggerWorker.KEY_SEASON to season,
                        com.footpredict.local.data.notification.PredictionTriggerWorker.KEY_MIN_CONF to 70.0,
                        com.footpredict.local.data.notification.PredictionTriggerWorker.KEY_MIN_EDGE to 0.15
                    )
                )
                .build()
            androidx.work.WorkManager.getInstance(appContext).enqueueUniqueWork(
                "${com.footpredict.local.data.notification.PredictionTriggerWorker.UNIQUE_PREFIX}-$triggerLeague-$season",
                androidx.work.ExistingWorkPolicy.REPLACE,
                request
            )
        } catch (_: Exception) {
        }
    }

    fun dismissNotifPrompt() {
        _uiState.update { it.copy(showNotifPrompt = false) }
    }

    fun toggleStats(show: Boolean) {
        _uiState.update { it.copy(showStats = show, exportMessage = null) }
    }

    /**
     * Gestion du capital v1.3.0 (Réglages → Coupon du Jour) : valeurs
     * assainies (capital > 0 conservé, mise dans [0, capital]), jamais
     * de crash sur saisie invalide.
     */
    fun updateBankroll(capital: Double, flat: Double, strategy: com.footpredict.local.domain.model.StakingStrategy) {
        _uiState.update { s ->
            val cap = if (capital > 0.0) capital else s.bankrollCapital
            s.copy(
                bankrollCapital = cap,
                flatStake = flat.coerceIn(0.0, cap),
                stakingStrategy = strategy
            )
        }
    }

    fun clearExportMessage() {
        _uiState.update { it.copy(exportMessage = null) }
    }

    /**
     * Prépare l’export : l’UI ouvre ensuite le sélecteur de destination
     * système (l’utilisateur choisit où enregistrer — exigence produit).
     */
    fun exportHistory() {
        viewModelScope.launch {
            try {
                val s = _uiState.value
                val byId = allMatches.associateBy { it.fixture.id }
                val rows = mutableListOf<com.footpredict.local.data.export.HistoryRow>()
                for (sp in local.getAllSettled().map { it.toDomain() }) {
                    val em = byId[sp.matchId] ?: continue
                    rows += settledRow(em, sp)
                }
                for ((id, pred) in s.predictions) {
                    val em = byId[id] ?: continue
                    if (em.fixture.statusShort == "FT") continue // déjà dans settled
                    rows += pendingRow(em, pred)
                }
                val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
                    .format(java.util.Date())
                _uiState.update {
                    it.copy(
                        pendingExport = PendingExport(
                            fileName = "footpredict_historique_$stamp.csv",
                            content = com.footpredict.local.data.export.historyToCsv(rows)
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(exportMessage = "ERR:" + (e.message ?: "?")) }
            }
        }
    }

    /**
     * Écrit le CSV vers l’URI choisie (SAF), avec BOM UTF-8 explicite pour Excel.
     */
    fun writeExportToUri(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val pending = _uiState.value.pendingExport
                    ?: throw IllegalStateException("aucun export en attente")
                appContext.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                    out.write(pending.content.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("flux inaccessible")
                _uiState.update { it.copy(pendingExport = null, exportMessage = "OK:" + uri.toString()) }
            } catch (e: Exception) {
                _uiState.update { it.copy(pendingExport = null, exportMessage = "ERR:" + (e.message ?: "?")) }
            }
        }
    }

    fun cancelExport() {
        _uiState.update { it.copy(pendingExport = null) }
    }

    private fun settledRow(
        em: EnrichedMatch,
        sp: com.footpredict.local.domain.model.SettledPrediction
    ): com.footpredict.local.data.export.HistoryRow {
        val f = em.fixture
        val predictedProb = when (sp.predictedOutcome) {
            "HOME" -> sp.homeWinProb
            "AWAY" -> sp.awayWinProb
            else -> sp.drawProb
        }
        val best = com.footpredict.local.domain.model.bestMarketPick(
            sp.homeWinProb, sp.drawProb, sp.awayWinProb, sp.over25Prob, sp.bttsYesProb
        )
        val bestOdd = sp.bookmakerOdds
            ?: listOfNotNull(em.odds?.homeOdds, em.odds?.drawOdds, em.odds?.awayOdds).maxOrNull()
        // Simulation comptable 10 € (v1.3.0) : +[cote×10−10] si GAGNÉ,
        // −10 si PERDU, null si cote absente (jamais inventée). La cote
        // FIGÉE au soldé prime (audit 2026-10-14 : ne pas dépendre des
        // cotes courantes, qui disparaissent après le match).
        val simulation = if (bestOdd != null && bestOdd > 1.0) {
            if (sp.wasHit) bestOdd * 10.0 - 10.0 else -10.0
        } else null
        return com.footpredict.local.data.export.HistoryRow(
            matchId = f.id.toString(),
            matchDate = java.time.Instant.ofEpochSecond(f.dateTimestamp).toString(),
            competitionName = PickHelpers.LEAGUE_NAMES[f.leagueId] ?: "Ligue ${f.leagueId}",
            homeTeam = f.homeTeamName,
            awayTeam = f.awayTeamName,
            finalScoreHome = f.goalsHome,
            finalScoreAway = f.goalsAway,
            appMarketProposal = proposalLabel(f.homeTeamName, f.awayTeamName, best),
            appConfidencePct = sp.confidence * 100,
            bookmakerOdds = bestOdd,
            bookmakerImpliedPct = bestOdd?.let { 100.0 / it },
            valueBetEdgePct = sp.edgeOnPredicted?.times(100),
            totalXgHome = f.xgHome,
            totalXgAway = f.xgAway,
            predictionStatus = if (sp.wasHit) {
                com.footpredict.local.data.export.HistoryRow.STATUS_WON
            } else {
                com.footpredict.local.data.export.HistoryRow.STATUS_LOST
            },
            predictionErrorMargin = kotlin.math.abs(predictedProb - if (sp.wasHit) 1.0 else 0.0),
            simulationGain10e = simulation
        )
    }

    private fun pendingRow(
        em: EnrichedMatch,
        pred: com.footpredict.local.domain.model.EnsemblePrediction
    ): com.footpredict.local.data.export.HistoryRow {
        val f = em.fixture
        val best = com.footpredict.local.domain.model.bestMarketPick(
            pred.homeWinProb, pred.drawProb, pred.awayWinProb,
            pred.over25Prob, pred.bttsYesProb
        )
        val bestOdd = listOfNotNull(em.odds?.homeOdds, em.odds?.drawOdds, em.odds?.awayOdds).maxOrNull()
        return com.footpredict.local.data.export.HistoryRow(
            matchId = f.id.toString(),
            matchDate = java.time.Instant.ofEpochSecond(f.dateTimestamp).toString(),
            competitionName = PickHelpers.LEAGUE_NAMES[f.leagueId] ?: "Ligue ${f.leagueId}",
            homeTeam = f.homeTeamName,
            awayTeam = f.awayTeamName,
            finalScoreHome = null,
            finalScoreAway = null,
            appMarketProposal = proposalLabel(f.homeTeamName, f.awayTeamName, best),
            appConfidencePct = pred.confidence * 100,
            bookmakerOdds = bestOdd,
            bookmakerImpliedPct = bestOdd?.let { 100.0 / it },
            valueBetEdgePct = null,
            totalXgHome = f.xgHome,
            totalXgAway = f.xgAway,
            predictionStatus = com.footpredict.local.data.export.HistoryRow.STATUS_PENDING,
            predictionErrorMargin = null
        )
    }

    /**
     * Libellé FR de la proposition (même vocabulaire que la carte) depuis
     * le meilleur pick tous marchés (1X2, Over/Under, BTTS).
     */
    private fun proposalLabel(
        home: String,
        away: String,
        best: com.footpredict.local.domain.model.BestPick
    ): String = when (best.kind) {
        com.footpredict.local.domain.model.MarketKind.HOME_WIN -> "Victoire $home"
        com.footpredict.local.domain.model.MarketKind.AWAY_WIN -> "Victoire $away"
        com.footpredict.local.domain.model.MarketKind.DRAW -> "Match nul"
        com.footpredict.local.domain.model.MarketKind.OVER25 -> "Over 2.5"
        com.footpredict.local.domain.model.MarketKind.UNDER25 -> "Under 2.5"
        com.footpredict.local.domain.model.MarketKind.BTTS_YES -> "BTTS - Yes"
        com.footpredict.local.domain.model.MarketKind.BTTS_NO -> "BTTS - No"
        else -> "Victoire $home"
    }

    /**
     * Value Alert : chute de cote > 10 % vs la dernière valeur vue en session.
     * Limite documentée : uniquement intra-session (l’historique inter-sessions
     * vit dans odds_snapshots, exploité par le graphique du détail).
     */
    private fun dropAlerts(matches: List<EnrichedMatch>): List<BettingAlert> {        val current = matches.mapNotNull { em -> em.odds?.let { em.fixture.id to it } }.toMap()
        val alerts = mutableListOf<BettingAlert>()
        for ((id, odds) in current) {
            val prev = prevOdds[id] ?: continue
            val drops = oddsDrop(prev, odds) ?: continue
            val (side, pct) = drops
            val em = matches.firstOrNull { it.fixture.id == id } ?: continue
            // Langage courant : codes d’issue « 1/X/2 » traduits, sans injonction.
            val sideLabel = when (side) {
                "1" -> "victoire ${em.fixture.homeTeamName}"
                "2" -> "victoire ${em.fixture.awayTeamName}"
                else -> "match nul"
            }
            alerts += BettingAlert(
                id = "drop_${id}_${side}",
                matchId = id,
                homeTeam = em.fixture.homeTeamName,
                awayTeam = em.fixture.awayTeamName,
                type = AlertType.DROPPING_ODDS,
                severity = if (pct >= 20.0) AlertSeverity.HIGH else AlertSeverity.MEDIUM,
                title = "Chute de cote",
                message = "${em.fixture.homeTeamName} vs ${em.fixture.awayTeamName} — la cote « $sideLabel » baisse de ${"%.0f".format(pct)} %",
                confidence = null
            )
        }
        prevOdds = current
        return alerts
    }
    fun selectMatch(matchId: Int) {
        viewModelScope.launch {
            try {
                val em = allMatches.firstOrNull { it.fixture.id == matchId } ?: return@launch
                val pred = _uiState.value.predictions[matchId]
                val market = em.odds?.let { marketModel(it) }
                val (favProb, favImplied) = favoriteVsMarket(pred, market)
                val homeTrend = xgTrend(em.fixture.homeTeamId)
                val awayTrend = xgTrend(em.fixture.awayTeamId)
                val h2hRows = try {
                    local.getHeadToHead(em.fixture.homeTeamId, em.fixture.awayTeamId, 10)
                } catch (_: Exception) {
                    emptyList()
                }
                val history = try {
                    local.getOddsHistory(matchId)
                } catch (_: Exception) {
                    emptyList()
                }
                _uiState.update {
                    it.copy(
                        selectedDetail = MatchDetail(
                            match = em,
                            prediction = pred,
                            marketPickProb = favImplied,
                            valueEdge = if (favProb != null && favImplied != null) favProb - favImplied else null,
                            xgHomeTrend = homeTrend,
                            xgAwayTrend = awayTrend,
                            h2h = computeH2H(h2hRows, em.fixture.homeTeamId, em.fixture.awayTeamId),
                            oddsHistory = history.mapNotNull { row ->
                                val h = row.homeOdds?.takeIf { o -> o > 1.0 }?.let { 1.0 / it }
                                val d = row.drawOdds?.takeIf { o -> o > 1.0 }?.let { 1.0 / it }
                                val a = row.awayOdds?.takeIf { o -> o > 1.0 }?.let { 1.0 / it }
                                val m = (h ?: 0.0) + (d ?: 0.0) + (a ?: 0.0)
                                if (m <= 0.0) null else OddsPoint(
                                    timestamp = row.timestamp,
                                    homeProb = h?.div(m),
                                    drawProb = d?.div(m),
                                    awayProb = a?.div(m)
                                )
                            }
                        )
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedDetail = null) }
    }

    /** xG générés sur les 5 derniers matchs terminés renseignés (chronologique). */
    private suspend fun xgTrend(teamId: Int): List<Double> = try {
        local.getLastFinishedForTeam(teamId, 25)
            .sortedBy { it.dateTimestamp }
            .mapNotNull { m ->
                if (m.homeTeamId == teamId) m.xgHome else m.xgAway
            }.takeLast(5)
    } catch (_: Exception) {
        emptyList()
    }

    override fun onCleared() {
        stopLivePolling()
        super.onCleared()
    }
}
