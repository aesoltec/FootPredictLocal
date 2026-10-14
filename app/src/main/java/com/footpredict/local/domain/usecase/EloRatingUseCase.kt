package com.footpredict.local.domain.usecase

import com.footpredict.local.domain.model.EloPrediction
import com.footpredict.local.domain.model.Match
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.exp
import kotlin.math.pow

/**
 * UseCase pur — conversion Elo → probabilités 1X2 + mise à jour des ratings.
 *
 * - HFA = 65 points
 * - Formule logistique standard
 * - Draw estimé via formule empirique (écart Elo)
 * - Mise à jour : R' = R + K × (résultat - attendu), K = 20 par défaut
 */
class EloRatingUseCase @Inject constructor() {

    companion object {
        private const val HFA = 65.0
        private const val DEFAULT_ELO = 1500.0
        const val K_DEFAULT = 20.0
    }

    suspend operator fun invoke(
        match: Match,
        eloHome: Double = DEFAULT_ELO,
        eloAway: Double = DEFAULT_ELO
    ): EloPrediction = withContext(Dispatchers.Default) {

        val expectedHome = 1.0 / (1.0 + 10.0.pow((eloAway - eloHome - HFA) / 400.0))
        val expectedAway = 1.0 - expectedHome

        // Estimation du nul : plus l’écart est faible, plus le nul est probable
        val diff = kotlin.math.abs(eloHome - eloAway)
        val drawProb = (0.28 * exp(-diff / 200.0)).coerceIn(0.10, 0.32)

        val remaining = 1.0 - drawProb
        val homeProb = expectedHome * remaining
        val awayProb = expectedAway * remaining

        EloPrediction(
            matchId = match.id,
            homeProb = homeProb,
            drawProb = drawProb,
            awayProb = awayProb,
            eloHome = eloHome,
            eloAway = eloAway
        )
    }

    /**
     * Met à jour les deux ratings après un match terminé (pur, testable).
     * @return paire (nouveau Elo domicile, nouveau Elo extérieur).
     */
    fun update(
        eloHome: Double,
        eloAway: Double,
        goalsHome: Int,
        goalsAway: Int,
        k: Double = K_DEFAULT
    ): Pair<Double, Double> {
        val expectedHome = 1.0 / (1.0 + 10.0.pow((eloAway - eloHome - HFA) / 400.0))
        val (scoreHome, scoreAway) = when {
            goalsHome > goalsAway -> 1.0 to 0.0
            goalsHome < goalsAway -> 0.0 to 1.0
            else -> 0.5 to 0.5
        }
        return (eloHome + k * (scoreHome - expectedHome)) to
            (eloAway + k * (scoreAway - (1.0 - expectedHome)))
    }
}
