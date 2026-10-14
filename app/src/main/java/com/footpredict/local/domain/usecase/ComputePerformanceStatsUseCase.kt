package com.footpredict.local.domain.usecase

import com.footpredict.local.domain.model.OutcomeStats
import com.footpredict.local.domain.model.PerformanceStats
import com.footpredict.local.domain.model.SettledPrediction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.pow

/**
 * UseCase pur — calcule les stats de performance à partir des prédictions settled.
 *
 * Métriques :
 * - Hit rate global et par issue (1/X/2)
 * - Hit rate haute confiance (conf ≥ 0.55)
 * - Hit rate value bets
 * - Brier score (calibration)
 */
class ComputePerformanceStatsUseCase @Inject constructor() {

    suspend operator fun invoke(
        settled: List<SettledPrediction>
    ): PerformanceStats = withContext(Dispatchers.Default) {
        if (settled.isEmpty()) return@withContext PerformanceStats()

        val hits = settled.count { it.wasHit }
        val total = settled.size
        val hitRate = hits.toDouble() / total

        val avgConf = settled.map { it.confidence }.average()

        val highConf = settled.filter { it.confidence >= 0.55 }
        val highConfHitRate = if (highConf.isNotEmpty()) {
            highConf.count { it.wasHit }.toDouble() / highConf.size
        } else 0.0

        val valueBets = settled.filter { it.edgeOnPredicted != null && it.edgeOnPredicted!! >= 0.10 }
        val valueBetHits = valueBets.count { it.wasHit }
        val valueBetHitRate = if (valueBets.isNotEmpty()) {
            valueBetHits.toDouble() / valueBets.size
        } else 0.0

        // Brier score (moyenne des (p - outcome)² sur les 3 issues)
        val brier = settled.map { sp ->
            val pHome = sp.homeWinProb
            val pDraw = sp.drawProb
            val pAway = sp.awayWinProb
            val oHome = if (sp.actualOutcome == "HOME") 1.0 else 0.0
            val oDraw = if (sp.actualOutcome == "DRAW") 1.0 else 0.0
            val oAway = if (sp.actualOutcome == "AWAY") 1.0 else 0.0
            (pHome - oHome).pow(2) + (pDraw - oDraw).pow(2) + (pAway - oAway).pow(2)
        }.average()

        fun byOutcome(code: String): OutcomeStats {
            val subset = settled.filter { it.predictedOutcome == code }
            val h = subset.count { it.wasHit }
            return OutcomeStats(
                predicted = subset.size,
                hits = h,
                hitRate = if (subset.isNotEmpty()) h.toDouble() / subset.size else 0.0
            )
        }

        PerformanceStats(
            totalPredictions = total,
            hits = hits,
            hitRate = hitRate,
            avgConfidence = avgConf,
            highConfidenceHitRate = highConfHitRate,
            valueBetCount = valueBets.size,
            valueBetHits = valueBetHits,
            valueBetHitRate = valueBetHitRate,
            brierScore = brier,
            byOutcome = mapOf(
                "HOME" to byOutcome("HOME"),
                "DRAW" to byOutcome("DRAW"),
                "AWAY" to byOutcome("AWAY")
            ),
            lastUpdated = System.currentTimeMillis()
        )
    }
}
