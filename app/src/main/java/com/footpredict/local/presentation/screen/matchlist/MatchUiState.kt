package com.footpredict.local.presentation.screen.matchlist

import com.footpredict.local.domain.model.BettingAlert
import com.footpredict.local.domain.model.EnrichedMatch
import com.footpredict.local.domain.model.EnsemblePrediction
import com.footpredict.local.domain.model.LivePredictionHistory
import com.footpredict.local.domain.model.PerformanceStats

enum class StatusFilter { ALL, UPCOMING, FINISHED }

/** Confrontations directes : victoires A / nuls / victoires B (A = domicile du match). */
data class H2HStats(
    val homeWins: Int = 0,
    val draws: Int = 0,
    val awayWins: Int = 0
) {
    val total: Int get() = homeWins + draws + awayWins
}

/** Point d’historique de cotes (probabilité implicite 1X2 à un instant t). */
data class OddsPoint(
    val timestamp: Long,
    val homeProb: Double?,
    val drawProb: Double?,
    val awayProb: Double?
)

/**
 * Données de l’écran de détail d’un match. Tout est local (offline-first) :
 * `null` / liste vide = section affichée en état « données insuffisantes »,
 * jamais de valeurs inventées.
 */
data class MatchDetail(
    val match: EnrichedMatch,
    val prediction: EnsemblePrediction?,
    /** Probabilités implicites marché pour l’issue favorite (donut). */
    val marketPickProb: Double?,
    /** Écart app − bookmaker sur l’issue favorite (value). */
    val valueEdge: Double?,
    val xgHomeTrend: List<Double>,
    val xgAwayTrend: List<Double>,
    val h2h: H2HStats,
    val oddsHistory: List<OddsPoint>
)

data class MatchUiState(
    val isLoading: Boolean = false,
    val matches: List<EnrichedMatch> = emptyList(),
    val predictions: Map<Int, EnsemblePrediction> = emptyMap(),
    val liveHistories: Map<Int, LivePredictionHistory> = emptyMap(),
    val alerts: List<BettingAlert> = emptyList(),
    val performance: PerformanceStats? = null,
    val errorMessage: String? = null,
    val isFromCache: Boolean = false,
    val isOffline: Boolean = false,
    val isLiveRefreshing: Boolean = false,
    val liveAutoRefreshEnabled: Boolean = true,
    val lastLiveRefreshAt: Long? = null,
    val leagueId: Int = 39,
    val query: String = "",
    val statusFilter: StatusFilter = StatusFilter.ALL,
    val minConfidence: Float = 0f,
    val topPicks: List<TopPick> = emptyList(),
    val avoidPicks: List<TopPick> = emptyList(),
    val topOver: List<TopPick> = emptyList(),
    val topUnder: List<TopPick> = emptyList(),
    val topBttsYes: List<TopPick> = emptyList(),
    val topBttsNo: List<TopPick> = emptyList(),
    val topDC: List<TopPick> = emptyList(),
    /** Terminés triés desc (écran dédié, vert/rouge via settledMap). */
    val finishedMatches: List<EnrichedMatch> = emptyList(),
    val showNotifPrompt: Boolean = false,
    val selectedDetail: MatchDetail? = null,
    val windows: FilterWindows = FilterWindows(),
    /** Intervalle personnalisé validé en ms (null = fenêtres par défaut). */
    val dateRangeMs: Pair<Long, Long>? = null,
    val showStats: Boolean = false,
    val exportMessage: String? = null,
    /** Export prêt : l’UI ouvre le sélecteur de destination (SAF). */
    val pendingExport: PendingExport? = null,
    val summary: PerformanceSummary = PerformanceSummary(),
    /** Pronostics terminés par match (cartes vert/rouge). */
    val settledMap: Map<Int, com.footpredict.local.domain.model.SettledPrediction> = emptyMap(),
    /** Coupon auto (null = rien ne passe la sécurité). */
    val coupon: Coupon? = null,
    /** Safe du jour v1.3.2 (null = aucune ligne à 88 %, état honnête). */
    val safeCoupon: SafeCoupon? = null,
    /** Progression du calcul (faits/total), null si inactif. */
    val computeProgress: Pair<Int, Int>? = null,
    /** Gestion du capital v1.3.0 : partagée Réglages ↔ Coupon du Jour. */
    val bankrollCapital: Double = com.footpredict.local.domain.model.DEFAULT_BANKROLL,
    val flatStake: Double = com.footpredict.local.domain.model.FLAT_DEFAULT_STAKE,
    val stakingStrategy: com.footpredict.local.domain.model.StakingStrategy =
        com.footpredict.local.domain.model.StakingStrategy.FLAT
)

/** Contenu CSV prêt à écrire vers l’URI choisie par l’utilisateur. */
data class PendingExport(
    val fileName: String,
    val content: String
)

/**
 * Résumé chiffré pour l’écran Stats (5 lignes exigées) : calculé depuis
 * `settled_predictions` (GAGNÉ/PERDU) + pronostics en attente.
 * Taux = gagnés / total × 100 (0 si aucun match résolu).
 */
data class PerformanceSummary(
    val totalPredicted: Int = 0,
    val won: Int = 0,
    val lost: Int = 0,
    val pending: Int = 0,
    val hitRatePct: Double = 0.0
)

/** Construction pure (testée) du résumé. */
fun buildSummary(
    settled: List<com.footpredict.local.domain.model.SettledPrediction>,
    pending: Int
): PerformanceSummary {
    val won = settled.count { it.wasHit }
    val total = settled.size
    return PerformanceSummary(
        totalPredicted = total,
        won = won,
        lost = total - won,
        pending = pending.coerceAtLeast(0),
        hitRatePct = if (total > 0) won * 100.0 / total else 0.0
    )
}

/**
 * Pronostic soldé → prédiction affichable (pur, testé) : l’écran Terminés
 * n’a pas de prédiction « en direct » (match fini), on rejoue donc les
 * probabilités mémorisées AVANT match (mêmes chiffres que l’export CSV).
 * Divergence neutre (MEDIUM, info non mémorisée), sans value (marché exclu
 * du rejoué). Les lignes Buts / « Les deux marquent » / Double Chance /
 * Corners des cartes terminées viennent de là.
 */
fun settledToCardPrediction(
    sp: com.footpredict.local.domain.model.SettledPrediction
): com.footpredict.local.domain.model.EnsemblePrediction =
    com.footpredict.local.domain.model.EnsemblePrediction(
        matchId = sp.matchId,
        homeWinProb = sp.homeWinProb,
        drawProb = sp.drawProb,
        awayWinProb = sp.awayWinProb,
        over25Prob = sp.over25Prob,
        confidence = sp.confidence,
        divergenceLevel = com.footpredict.local.domain.model.DivergenceLevel.MEDIUM,
        dixonColes = null,
        market = null,
        elo = null,
        valueBets = emptyList(),
        bttsYesProb = sp.bttsYesProb
    )
