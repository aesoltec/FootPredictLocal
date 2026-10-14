package com.footpredict.local.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitaires — prédiction live "rest of match".
 */
class CalculateLiveRestOfMatchUseCaseTest {

    private val useCase = CalculateLiveRestOfMatchUseCase()

    @Test
    fun `kickoff probabilities sum to 1`() = runTest {
        val pred = useCase(
            matchId = 1, lambdaHome = 1.6, lambdaAway = 1.2,
            goalsHome = 0, goalsAway = 0, elapsedMinutes = 0, statusShort = "1H"
        )
        assertEquals(1.0, pred.homeWinProb + pred.drawProb + pred.awayWinProb, 0.02)
        assertTrue(pred.over25Prob in 0.0..1.0)
    }

    @Test
    fun `leading team late has very high win probability`() = runTest {
        val pred = useCase(
            matchId = 1, lambdaHome = 1.5, lambdaAway = 1.2,
            goalsHome = 2, goalsAway = 0, elapsedMinutes = 80, statusShort = "2H"
        )
        assertTrue(pred.homeWinProb > 0.85)
    }

    @Test
    fun `tied late raises draw probability`() = runTest {
        val early = useCase(
            matchId = 1, lambdaHome = 1.5, lambdaAway = 1.2,
            goalsHome = 0, goalsAway = 0, elapsedMinutes = 5, statusShort = "1H"
        )
        val late = useCase(
            matchId = 1, lambdaHome = 1.5, lambdaAway = 1.2,
            goalsHome = 0, goalsAway = 0, elapsedMinutes = 85, statusShort = "2H"
        )
        assertTrue(late.drawProb > early.drawProb)
    }

    @Test
    fun `toEnsemble merges live and market`() = runTest {
        val live = useCase(
            matchId = 3, lambdaHome = 1.5, lambdaAway = 1.2,
            goalsHome = 1, goalsAway = 0, elapsedMinutes = 60, statusShort = "2H"
        )
        val ensemble = useCase.toEnsemble(
            matchId = 3, liveRom = live, marketLive = null, prematchConfidence = 0.5
        )
        assertEquals(1.0, ensemble.homeWinProb + ensemble.drawProb + ensemble.awayWinProb, 0.02)
        assertTrue(ensemble.confidence in 0.0..0.95)
    }
}
