package com.footpredict.local.domain.model

/**
 * Snapshot d’évolution d’une prédiction en direct (pendant le match).
 */
data class LivePredictionSnapshot(
    val matchId: Int,
    val elapsed: Int?,              // minutes jouées
    val statusShort: String,        // 1H, HT, 2H, ET…
    val goalsHome: Int?,
    val goalsAway: Int?,
    val homeWinProb: Double,
    val drawProb: Double,
    val awayWinProb: Double,
    val confidence: Double,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Historique d’évolution pour un match live.
 */
data class LivePredictionHistory(
    val matchId: Int,
    val snapshots: List<LivePredictionSnapshot> = emptyList()
) {
    val latest: LivePredictionSnapshot? get() = snapshots.lastOrNull()
}
