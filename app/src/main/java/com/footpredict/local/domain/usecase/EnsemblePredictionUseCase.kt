package com.footpredict.local.domain.usecase

import com.footpredict.local.domain.model.DivergenceLevel
import com.footpredict.local.domain.model.EloPrediction
import com.footpredict.local.domain.model.EnsemblePrediction
import com.footpredict.local.domain.model.MarketPrediction
import com.footpredict.local.domain.model.MatchPrediction
import com.footpredict.local.domain.model.ValueBet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.sqrt

/**
 * UseCase pur — agrégation des 3 modèles indépendants.
 *
 * Pondération par défaut :
 *   3 modèles → DC 0.40 / Marché 0.40 / Elo 0.20
 *   2 modèles → redistribution proportionnelle
 *   1 modèle  → poids 1.0
 *
 * Confiance ajustée selon le désaccord (écart-type) + bonus nombre de modèles.
 * Value bet si edge ≥ 0.10.
 */
class EnsemblePredictionUseCase @Inject constructor() {

    suspend operator fun invoke(
        matchId: Int,
        dixonColes: MatchPrediction?,
        market: MarketPrediction?,
        elo: EloPrediction?
    ): EnsemblePrediction = withContext(Dispatchers.Default) {

        val models = listOfNotNull(
            dixonColes?.let { Triple(it.homeWinProb, it.drawProb, it.awayWinProb) },
            market?.let { Triple(it.homeProb, it.drawProb, it.awayProb) },
            elo?.let { Triple(it.homeProb, it.drawProb, it.awayProb) }
        )

        if (models.isEmpty()) {
            return@withContext EnsemblePrediction(
                matchId = matchId,
                homeWinProb = 0.33,
                drawProb = 0.34,
                awayWinProb = 0.33,
                over25Prob = null,
                confidence = 0.0,
                divergenceLevel = DivergenceLevel.HIGH,
                dixonColes = null,
                market = null,
                elo = null
            )
        }

        // Poids
        val weights = when (models.size) {
            3 -> listOf(0.40, 0.40, 0.20)
            2 -> listOf(0.55, 0.45)
            else -> listOf(1.0)
        }

        var h = 0.0
        var d = 0.0
        var a = 0.0
        models.forEachIndexed { i, (ph, pd, pa) ->
            h += ph * weights[i]
            d += pd * weights[i]
            a += pa * weights[i]
        }
        val sum = h + d + a
        if (sum > 0) {
            h /= sum; d /= sum; a /= sum
        }

        // Écart-type (désaccord)
        val homeProbs = models.map { it.first }
        val sigma = stdDev(homeProbs)

        val agreementFactor = when {
            sigma < 0.03 -> 1.05
            sigma < 0.08 -> 1.00
            sigma < 0.15 -> 0.90
            else -> 0.75
        }
        val modelsBonus = when (models.size) {
            3 -> 1.03
            2 -> 1.00
            else -> 0.95
        }

        val maxP = maxOf(h, d, a)
        val confidence = (maxP * agreementFactor * modelsBonus).coerceIn(0.0, 0.99)

        val divergence = when {
            sigma < 0.03 -> DivergenceLevel.LOW
            sigma < 0.15 -> DivergenceLevel.MEDIUM
            else -> DivergenceLevel.HIGH
        }

        // Value bets
        val valueBets = mutableListOf<ValueBet>()
        if (market != null) {
            checkValue("HOME", h, market.homeProb)?.let { valueBets += it }
            checkValue("DRAW", d, market.drawProb)?.let { valueBets += it }
            checkValue("AWAY", a, market.awayProb)?.let { valueBets += it }
        }

        EnsemblePrediction(
            matchId = matchId,
            homeWinProb = h,
            drawProb = d,
            awayWinProb = a,
            over25Prob = dixonColes?.over25Prob,
            confidence = confidence,
            divergenceLevel = divergence,
            dixonColes = dixonColes,
            market = market,
            elo = elo,
            valueBets = valueBets,
            bttsYesProb = dixonColes?.bttsYesProb,
            over15Prob = dixonColes?.over15Prob,
            over35Prob = dixonColes?.over35Prob
        )
    }

    private fun checkValue(outcome: String, ensemble: Double, implied: Double): ValueBet? {
        val edge = ensemble - implied
        return if (edge >= 0.10) ValueBet(outcome, edge, ensemble, implied) else null
    }

    private fun stdDev(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance)
    }
}
