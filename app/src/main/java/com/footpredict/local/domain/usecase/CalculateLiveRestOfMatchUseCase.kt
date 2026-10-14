package com.footpredict.local.domain.usecase

import com.footpredict.local.domain.model.EnsemblePrediction
import com.footpredict.local.domain.model.MatchPrediction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.exp
import kotlin.math.pow

/**
 * UseCase pur — prédiction live "rest of match".
 *
 * Principe :
 * - On part des λ pré-match (Dixon-Coles)
 * - On réduit proportionnellement au temps restant
 * - On conditionne sur le score actuel : P(final | score_t)
 * - Poisson indépendant sur les buts restants (i, j ∈ [0..5])
 *
 * Sortie : probas 1X2 finales + O/U 2.5 conditionnel.
 */
class CalculateLiveRestOfMatchUseCase @Inject constructor() {

    companion object {
        private const val FULL_TIME_MIN = 90.0
        private const val MAX_EXTRA = 5          // buts restants max modélisés par équipe
    }

    /**
     * @param lambdaHome λ pré-match domicile
     * @param lambdaAway λ pré-match extérieur
     * @param goalsHome score actuel domicile
     * @param goalsAway score actuel extérieur
     * @param elapsedMinutes minutes jouées (null → estimation HT=45, 2H=70, sinon 0)
     * @param statusShort statut API (1H, HT, 2H, ET…)
     */
    suspend operator fun invoke(
        matchId: Int,
        lambdaHome: Double,
        lambdaAway: Double,
        goalsHome: Int,
        goalsAway: Int,
        elapsedMinutes: Int? = null,
        statusShort: String = "1H"
    ): MatchPrediction = withContext(Dispatchers.Default) {

        val elapsed = elapsedMinutes?.toDouble()
            ?: estimateElapsed(statusShort)

        // Fraction de temps restant (clamp 5 % – 100 %)
        val remainingFrac = ((FULL_TIME_MIN - elapsed) / FULL_TIME_MIN)
            .coerceIn(0.05, 1.0)

        // λ restants
        val lambdaRemH = (lambdaHome * remainingFrac).coerceAtLeast(0.01)
        val lambdaRemA = (lambdaAway * remainingFrac).coerceAtLeast(0.01)

        var pHome = 0.0
        var pDraw = 0.0
        var pAway = 0.0
        var pOver25 = 0.0
        var pBttsYes = 0.0
        var pOver15 = 0.0
        var pOver35 = 0.0
        var totalMass = 0.0

        for (i in 0..MAX_EXTRA) {
            val pI = poisson(i, lambdaRemH)
            for (j in 0..MAX_EXTRA) {
                val pJ = poisson(j, lambdaRemA)
                val p = pI * pJ
                totalMass += p

                val finalH = goalsHome + i
                val finalA = goalsAway + j

                when {
                    finalH > finalA -> pHome += p
                    finalH == finalA -> pDraw += p
                    else -> pAway += p
                }
                if (finalH + finalA > 2) pOver25 += p
                if (finalH + finalA > 1) pOver15 += p
                if (finalH + finalA > 3) pOver35 += p
                if (finalH >= 1 && finalA >= 1) pBttsYes += p
            }
        }

        if (totalMass > 0) {
            pHome /= totalMass
            pDraw /= totalMass
            pAway /= totalMass
            pOver25 /= totalMass
            pOver15 /= totalMass
            pOver35 /= totalMass
            pBttsYes /= totalMass
        }

        MatchPrediction(
            matchId = matchId,
            homeWinProb = pHome,
            drawProb = pDraw,
            awayWinProb = pAway,
            over25Prob = pOver25.coerceIn(0.0, 1.0),
            under25Prob = (1.0 - pOver25).coerceIn(0.0, 1.0),
            lambdaHome = lambdaRemH,
            lambdaAway = lambdaRemA,
            bttsYesProb = pBttsYes.coerceIn(0.0, 1.0),
            bttsNoProb = (1.0 - pBttsYes).coerceIn(0.0, 1.0),
            over15Prob = pOver15.coerceIn(0.0, 1.0),
            over35Prob = pOver35.coerceIn(0.0, 1.0)
        )
    }

    /**
     * Fusionne prédiction live rest-of-match avec le marché live (si dispo)
     * pour produire un EnsemblePrediction adapté au live.
     */
    suspend fun toEnsemble(
        matchId: Int,
        liveRom: MatchPrediction,
        marketLive: com.footpredict.local.domain.model.MarketPrediction?,
        prematchConfidence: Double = 0.5
    ): EnsemblePrediction = withContext(Dispatchers.Default) {
        val models = mutableListOf(
            Triple(liveRom.homeWinProb, liveRom.drawProb, liveRom.awayWinProb)
        )
        val weights = mutableListOf(0.65) // rest-of-match dominant en live

        if (marketLive != null) {
            models += Triple(marketLive.homeProb, marketLive.drawProb, marketLive.awayProb)
            weights += 0.35
            // renormaliser
            val s = weights.sum()
            weights[0] /= s
            weights[1] /= s
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

        val maxP = maxOf(h, d, a)
        // Confiance un peu plus basse en live (incertitude score/temps)
        val confidence = (maxP * 0.92 * (0.7 + 0.3 * prematchConfidence)).coerceIn(0.0, 0.95)

        EnsemblePrediction(
            matchId = matchId,
            homeWinProb = h,
            drawProb = d,
            awayWinProb = a,
            over25Prob = liveRom.over25Prob,
            confidence = confidence,
            divergenceLevel = if (marketLive != null) {
                val diff = kotlin.math.abs(liveRom.homeWinProb - marketLive.homeProb)
                when {
                    diff < 0.05 -> com.footpredict.local.domain.model.DivergenceLevel.LOW
                    diff < 0.12 -> com.footpredict.local.domain.model.DivergenceLevel.MEDIUM
                    else -> com.footpredict.local.domain.model.DivergenceLevel.HIGH
                }
            } else com.footpredict.local.domain.model.DivergenceLevel.MEDIUM,
            dixonColes = liveRom,
            market = marketLive,
            elo = null,
            valueBets = emptyList(),
            bttsYesProb = liveRom.bttsYesProb,
            over15Prob = liveRom.over15Prob,
            over35Prob = liveRom.over35Prob
        )
    }

    private fun estimateElapsed(status: String): Double = when (status) {
        "1H" -> 25.0
        "HT" -> 45.0
        "2H" -> 70.0
        "ET", "BT", "P" -> 95.0
        else -> 30.0
    }

    private fun poisson(k: Int, lambda: Double): Double {
        if (lambda <= 0.0) return if (k == 0) 1.0 else 0.0
        var fact = 1.0
        for (i in 2..k) fact *= i
        return exp(-lambda) * lambda.pow(k) / fact
    }
}
