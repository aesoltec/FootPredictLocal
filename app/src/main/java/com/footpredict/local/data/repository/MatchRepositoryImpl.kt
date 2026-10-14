package com.footpredict.local.data.repository

import com.footpredict.local.data.aggregator.DataFusionAggregator
import com.footpredict.local.data.aggregator.dedupeFixtures
import com.footpredict.local.data.aggregator.isSameFixture
import com.footpredict.local.data.aggregator.mergeXg
import com.footpredict.local.data.local.LocalDataSource
import com.footpredict.local.data.local.TeamLogoStore
import com.footpredict.local.data.remote.live.SupabaseLiveScoresDataSource
import com.footpredict.local.data.local.entity.EloEntity
import com.footpredict.local.data.local.entity.MatchEntity
import com.footpredict.local.data.mapper.EntityMappers.toDomain
import com.footpredict.local.domain.model.EnrichedMatch
import com.footpredict.local.domain.model.Resource
import com.footpredict.local.domain.repository.MatchRepository
import com.footpredict.local.domain.usecase.EloRatingUseCase
import com.footpredict.local.domain.usecase.ValidateLiveMatchesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Repository offline-first multi-sources (spec Section 6).
 *
 * 1. Émet immédiatement le cache Room (fromCache = true) si non vide
 * 2. Si cache frais (< 6h) → stop ici (économie de quota API)
 * 3. Sinon fetch parallèle via DataFusionAggregator
 * 4. Succès → upsert Room + réémet (fromCache = false)
 * 5. Échec → conserve le cache (déjà émis)
 */
class MatchRepositoryImpl @Inject constructor(
    private val local: LocalDataSource,
    private val aggregator: DataFusionAggregator,
    private val eloModel: EloRatingUseCase,
    private val liveValidator: ValidateLiveMatchesUseCase,
    private val logoStore: TeamLogoStore,
    private val supabaseLive: SupabaseLiveScoresDataSource
) : MatchRepository {

    companion object {
        private const val CACHE_MAX_AGE_MS = 6L * 60L * 60L * 1000L
    }

    override fun getEnrichedMatches(leagueId: Int, season: Int): Flow<Resource<List<EnrichedMatch>>> =
        flow {
            emit(Resource.Loading)

            val cachedEntities = try {
                local.observeMatchesByLeague(leagueId).first()
            } catch (_: Exception) {
                emptyList()
            }

            if (cachedEntities.isNotEmpty()) {
                val cachedEnriched = cachedEntities.map { entity ->
                    val homeStats = local.getTeamStats(entity.homeTeamId, entity.leagueId, entity.season)
                    val awayStats = local.getTeamStats(entity.awayTeamId, entity.leagueId, entity.season)
                    val odds = local.getOdds(entity.id)
                    val eloHome = local.getElo(entity.homeTeamId)?.rating
                    val eloAway = local.getElo(entity.awayTeamId)?.rating
                    EnrichedMatch(
                        fixture = entity.toDomain(),
                        homeStats = homeStats?.toDomain(),
                        awayStats = awayStats?.toDomain(),
                        odds = odds?.toDomain(),
                        eloHome = eloHome,
                        eloAway = eloAway,
                        sources = entity.sourcesJson?.split(",")?.map { it.trim() } ?: emptyList(),
                        conflicts = emptyList()
                    )
                }
                emit(Resource.Success(cachedEnriched, fromCache = true))
            }

            // Quota : si le cache est frais (< 6h), on ne tape pas le réseau.
            val nowMs = System.currentTimeMillis()
            val newestCache = cachedEntities.maxOfOrNull { it.lastUpdated } ?: 0L
            val isStale = cachedEntities.isEmpty() || (nowMs - newestCache) > CACHE_MAX_AGE_MS
            if (!isStale) return@flow

            try {
                val merged = aggregator.fetchMergedFixturesFull(leagueId, season)
                val sourcesUsed = merged.sourcesUsed
                // Anti-doublons inter-synchros (audit 2026-10-14) : la meilleure
                // source variant, un même match peut arriver sous un autre ID
                // (« Hull » vs « Hull City AFC »). On garde les lignes DÉJÀ en
                // base (historique soldé intact) et on écarte les doublons
                // entrants — jamais l’inverse (pas d’orphelins).
                // CPU lourd (comparaisons floues) déporté hors du thread
                // principal (audit ANR 2026-10-14 : plusieurs secondes de freeze).
                val incoming = withContext(Dispatchers.Default) {
                    val merged = dedupeFixtures(mergeXg(merged.fixtures, merged.alternates))
                    val known = cachedEntities.toList()
                    merged.filter { inc ->
                        known.none { it.id != inc.id && isSameFixture(it, inc) }
                    }
                }
                val fixtures = incoming.ifEmpty { cachedEntities }

                if (fixtures.isEmpty()) {
                    if (cachedEntities.isEmpty()) {
                        emit(Resource.Error("Aucune source disponible"))
                    }
                    return@flow
                }

                val now = System.currentTimeMillis()
                val stamped = fixtures.map {
                    it.copy(
                        sourcesJson = sourcesUsed.joinToString(","),
                        lastUpdated = now
                    )
                }
                local.upsertMatches(stamped)
                // Blasons persistés par nom de club (hors-ligne durable) :
                // best-effort, jamais bloquant pour la synchro.
                try {
                    logoStore.ensureCachedAll(
                        stamped.flatMap {
                            listOf(it.homeTeamName to it.homeTeamLogo, it.awayTeamName to it.awayTeamLogo)
                        }
                    )
                } catch (_: Exception) {
                }
                syncEloRatings(
                    stamped.filter {
                        it.statusShort == "FT" && it.goalsHome != null && it.goalsAway != null
                    }
                )

                // Batch : stats de toute la ligue en 1-2 appels (connexions lentes),
                // fusionnées dans Room (upsert intelligent). Le un-par-un ne sert
                // plus que de rattrapage pour les équipes encore inconnues.
                try {
                    aggregator.fetchLeagueStatsBatch(leagueId, season)
                        .forEach { local.upsertTeamStats(it) }
                } catch (_: Exception) {
                }
                val oddsBatch = try {
                    aggregator.fetchOddsBatch()
                } catch (_: Exception) {
                    emptyMap()
                }

                val enriched = stamped.map { entity ->
                    val homeStats = local.getTeamStats(
                        entity.homeTeamId, entity.leagueId, entity.season
                    ) ?: aggregator.fetchMergedTeamStats(
                        entity.homeTeamId, entity.leagueId, entity.season
                    )?.also { local.upsertTeamStats(it) }

                    val awayStats = local.getTeamStats(
                        entity.awayTeamId, entity.leagueId, entity.season
                    ) ?: aggregator.fetchMergedTeamStats(
                        entity.awayTeamId, entity.leagueId, entity.season
                    )?.also { local.upsertTeamStats(it) }

                    val odds = oddsBatch[entity.id]?.also {
                        local.upsertOdds(it)
                        local.recordOddsSnapshot(it)
                    } ?: aggregator.fetchMergedOdds(entity.id)?.also {
                        local.upsertOdds(it)
                        local.recordOddsSnapshot(it)
                    }

                    val eloHome = local.getElo(entity.homeTeamId)?.rating
                    val eloAway = local.getElo(entity.awayTeamId)?.rating

                    EnrichedMatch(
                        fixture = entity.toDomain(),
                        homeStats = homeStats?.toDomain(),
                        awayStats = awayStats?.toDomain(),
                        odds = odds?.toDomain(),
                        eloHome = eloHome,
                        eloAway = eloAway,
                        sources = sourcesUsed,
                        conflicts = emptyList()
                    )
                }

                emit(Resource.Success(enriched, fromCache = false))
            } catch (e: Exception) {
                if (cachedEntities.isEmpty()) {
                    emit(Resource.Error(e.message ?: "Erreur réseau"))
                }
            }
        }

    /**
     * Recalcule les ratings Elo depuis les matchs terminés (triés par date),
     * de façon déterministe : on rejoue tout l’historique connu DEPUIS 1500 à
     * chaque synchro, ce qui évite tout double comptage quand le même lot est
     * revu. Limite documentée : en multi-ligues, la dernière ligue synchronisée
     * impose ses ratings aux équipes partagées. Une erreur Elo ne fait jamais
     * échouer la synchro (dégradation gracieuse).
     */
    private suspend fun syncEloRatings(finished: List<MatchEntity>) {
        if (finished.isEmpty()) return
        try {
            val now = System.currentTimeMillis()
            val ratings = mutableMapOf<Int, EloEntity>()
            fun ratingOf(teamId: Int): EloEntity =
                ratings.getOrPut(teamId) {
                    EloEntity(teamId = teamId, rating = 1500.0, matchesPlayed = 0, lastUpdated = now)
                }
            for (m in finished.sortedBy { it.dateTimestamp }) {
                val home = ratingOf(m.homeTeamId)
                val away = ratingOf(m.awayTeamId)
                val (newHome, newAway) = eloModel.update(
                    eloHome = home.rating,
                    eloAway = away.rating,
                    goalsHome = m.goalsHome ?: continue,
                    goalsAway = m.goalsAway ?: continue
                )
                ratings[m.homeTeamId] = home.copy(
                    rating = newHome, matchesPlayed = home.matchesPlayed + 1, lastUpdated = now
                )
                ratings[m.awayTeamId] = away.copy(
                    rating = newAway, matchesPlayed = away.matchesPlayed + 1, lastUpdated = now
                )
            }
            for (elo in ratings.values) local.upsertElo(elo)
        } catch (_: Exception) {
            // Elo best-effort : les prédictions restent disponibles sans lui.
        }
    }

    /**
     * Synchro live EXCLUSIVE via la fonction mutualisée Supabase
     * `get-live-scores` (v1.3.3, cahier des charges : aucune requête live
     * directe vers des APIs tierces — clé API protégée côté serveur, quota
     * 100/j partagé via cache 90 s + plafond 40/j).
     *
     * La fonction renvoie en-cours + terminés du jour (~35 ligues ESPN +
     * top-up premium) : statuts/scores en direct, bascule NS→live au coup
     * d’envoi, FT rapide. Fonction non déployée / hors-ligne / quota → 0
     * silencieux (hors-ligne et synchros 6 h intacts).
     * Purge des reports lointains : reprise par les synchros complètes.
     *
     * Ne touche jamais aux matchs sans confirmation source (pas de score
     * inventé) ni aux FT en cache (pipeline settled propriétaire).
     *
     * Note d’architecte : PAS de FotMob/SofaScore ni d’appels directs aux
     * APIs tierces depuis le téléphone — endpoints non contractuels et
     * protégés, clé à protéger, quota à partager : tout passe par la fonction.
     */
    override suspend fun validateLiveMatches(leagueId: Int, season: Int): Int {
        return try {
            val nowMs = System.currentTimeMillis()
            val cached = local.observeMatchesByLeague(leagueId).first()
            // Guet : en cours + coups d’envoi imminents/récents non confirmés.
            val watch = cached.filter { entity ->
                val elapsedMin = (nowMs - entity.dateTimestamp * 1000) / 60_000L
                ValidateLiveMatchesUseCase.warrantsWatch(entity.statusShort, elapsedMin)
            }
            if (watch.isEmpty()) return 0
            // EXCLUSIF fonction (cahier des charges v1.3.3) : plus aucun appel
            // live direct aux APIs tierces. 1 appel couvre toutes les ligues.
            var applied = 0
            // fetchLiveScores ne lève jamais (vide en cas d’échec) : pas de try.
            val live = supabaseLive.fetchLiveScores()
            for (s in watch) {
                val match = live.firstOrNull { f ->
                    f.leagueId == s.leagueId &&
                        kotlin.math.abs(f.dateTimestamp - s.dateTimestamp) < 86400 &&
                        sameTeams(f, s)
                } ?: continue
                if (applyLiveUpdate(s, match, nowMs)) applied++
            }
            applied
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Applique une mise à jour live confirmée (jamais de score inventé :
     * décision [ValidateLiveMatchesUseCase.shouldApplyLiveUpdate], buts
     * confirmés nulls conservés). Retourne vrai si basculé.
     */
    private suspend fun applyLiveUpdate(s: MatchEntity, match: MatchEntity, nowMs: Long): Boolean {
        if (!ValidateLiveMatchesUseCase.shouldApplyLiveUpdate(
                s.statusShort, s.goalsHome, s.goalsAway,
                match.statusShort, match.goalsHome, match.goalsAway
            )
        ) return false
        local.upsertMatches(
            listOf(
                s.copy(
                    statusShort = match.statusShort,
                    statusLong = match.statusLong,
                    // Buts confirmés nulls (live, plan gratuit) : on garde
                    // les connus au lieu d’effacer le score affiché.
                    goalsHome = match.goalsHome ?: s.goalsHome,
                    goalsAway = match.goalsAway ?: s.goalsAway,
                    lastUpdated = nowMs
                )
            )
        )
        return true
    }

    private fun sameTeams(a: MatchEntity, b: MatchEntity): Boolean =
        norm(a.homeTeamName) == norm(b.homeTeamName) &&
            norm(a.awayTeamName) == norm(b.awayTeamName) ||
            (norm(a.homeTeamName).contains(norm(b.homeTeamName)) &&
                norm(a.awayTeamName).contains(norm(b.awayTeamName)))

    private fun norm(name: String): String =
        name.lowercase()
            .replace(NormRegex.NON_ALNUM, " ")
            .replace(NormRegex.SPACES, " ")
            .trim()
}

/** Regex mutualisées (audit ANR 2026-10-14 : jamais recompilées en boucle). */
private object NormRegex {
    val NON_ALNUM = Regex("[^a-z0-9 ]")
    val SPACES = Regex("\\s+")
}
