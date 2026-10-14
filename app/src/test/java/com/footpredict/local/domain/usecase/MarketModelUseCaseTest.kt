package com.footpredict.local.domain.usecase

import com.footpredict.local.domain.model.Odds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketModelUseCaseTest {

    private val useCase = MarketModelUseCase()

    @Test
    fun `implied probs sum to 1 after margin removal`() = runTest {
        val odds = Odds(
            matchId = 1,
            bookmaker = "Test",
            homeOdds = 2.10,
            drawOdds = 3.40,
            awayOdds = 3.50,
            source = "test"
        )
        val pred = useCase(odds)!!
        val sum = pred.homeProb + pred.drawProb + pred.awayProb
        assertEquals(1.0, sum, 0.001)
        assertTrue(pred.homeProb > 0 && pred.drawProb > 0 && pred.awayProb > 0)
    }

    @Test
    fun `favorite has highest probability`() = runTest {
        val odds = Odds(
            matchId = 1,
            bookmaker = "Test",
            homeOdds = 1.40,
            drawOdds = 4.50,
            awayOdds = 7.00,
            source = "test"
        )
        val pred = useCase(odds)!!
        assertTrue(pred.homeProb > pred.drawProb)
        assertTrue(pred.homeProb > pred.awayProb)
    }

    @Test
    fun `null odds returns null`() = runTest {
        val odds = Odds(
            matchId = 1,
            bookmaker = "Test",
            homeOdds = null,
            drawOdds = 3.0,
            awayOdds = 3.0,
            source = "test"
        )
        assertEquals(null, useCase(odds))
    }
}
