package com.footpredict.local.domain.usecase

import com.footpredict.local.domain.model.DivergenceLevel
import com.footpredict.local.domain.model.EloPrediction
import com.footpredict.local.domain.model.MarketPrediction
import com.footpredict.local.domain.model.MatchPrediction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitaires — Section 10/15 du prompt Dixon-Coles.
 * Agrégation 3 modèles + confiance + value bets.
 */
class EnsemblePredictionUseCaseTest {

    private val useCase = EnsemblePredictionUseCase()

    private fun dc(home: Double, draw: Double, away: Double) = MatchPrediction(
        matchId = 1, homeWinProb = home, drawProb = draw, awayWinProb = away,
        over25Prob = 0.5, under25Prob = 0.5, lambdaHome = 1.5, lambdaAway = 1.2
    )

    private fun market(home: Double, draw: Double, away: Double) = MarketPrediction(
        matchId = 1, homeProb = home, drawProb = draw, awayProb = away
    )

    private fun elo(home: Double, draw: Double, away: Double) = EloPrediction(
        matchId = 1, homeProb = home, drawProb = draw, awayProb = away,
        eloHome = 1600.0, eloAway = 1500.0
    )

    @Test
    fun `empty models returns high divergence and zero confidence`() = runTest {
        val pred = useCase(1, null, null, null)
        assertEquals(DivergenceLevel.HIGH, pred.divergenceLevel)
        assertEquals(0.0, pred.confidence, 0.0)
    }

    @Test
    fun `full agreement boosts confidence and low divergence`() = runTest {
        val pred = useCase(
            1,
            dc(0.60, 0.25, 0.15),
            market(0.60, 0.25, 0.15),
            elo(0.60, 0.25, 0.15)
        )
        assertEquals(DivergenceLevel.LOW, pred.divergenceLevel)
        // maxP=0.60 × 1.05 × 1.03 = 0.6489
        assertEquals(0.6489, pred.confidence, 0.001)
        assertTrue(pred.valueBets.isEmpty())
    }

    @Test
    fun `strong divergence penalizes confidence`() = runTest {
        val pred = useCase(
            1,
            dc(0.70, 0.20, 0.10),
            market(0.10, 0.20, 0.70),
            elo(0.40, 0.30, 0.30)
        )
        assertEquals(DivergenceLevel.HIGH, pred.divergenceLevel)
        assertTrue(pred.confidence < 0.45)
    }

    @Test
    fun `single model keeps its probabilities`() = runTest {
        val pred = useCase(7, dc(0.50, 0.30, 0.20), null, null)
        assertEquals(0.50, pred.homeWinProb, 0.001)
        assertEquals(0.30, pred.drawProb, 0.001)
        assertEquals(0.20, pred.awayWinProb, 0.001)
    }

    @Test
    fun `value bet detected when edge above 10 percent`() = runTest {
        // Ensemble home = 0.40×0.70+0.40×0.45+0.20×0.60 = 0.58 ; marché home = 0.45 → edge 0.13
        val pred = useCase(
            1,
            dc(0.70, 0.20, 0.10),
            market(0.45, 0.30, 0.25),
            elo(0.60, 0.25, 0.15)
        )
        assertTrue(pred.valueBets.any { it.outcome == "HOME" && it.edge >= 0.10 })
    }

    @Test
    fun `probabilities sum to 1`() = runTest {
        val pred = useCase(
            1,
            dc(0.50, 0.30, 0.20),
            market(0.40, 0.30, 0.30),
            elo(0.45, 0.25, 0.30)
        )
        assertEquals(1.0, pred.homeWinProb + pred.drawProb + pred.awayWinProb, 0.001)
    }
}
