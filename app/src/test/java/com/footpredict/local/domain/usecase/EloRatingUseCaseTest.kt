package com.footpredict.local.domain.usecase

import com.footpredict.local.domain.model.Match
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitaires — Elo (conversion + mise à jour après match terminé).
 */
class EloRatingUseCaseTest {

    private val useCase = EloRatingUseCase()

    private fun sampleMatch() = Match(
        id = 1, leagueId = 39, season = 2026, round = null,
        dateTimestamp = 1_791_631_800L, statusShort = "NS", statusLong = null,
        homeTeamId = 1, homeTeamName = "Arsenal", homeTeamLogo = null,
        awayTeamId = 2, awayTeamName = "Leeds", awayTeamLogo = null,
        goalsHome = null, goalsAway = null, venueName = null, venueCity = null
    )

    @Test
    fun `equal elos give balanced probabilities`() = runTest {
        val pred = useCase(sampleMatch(), 1500.0, 1500.0)
        assertEquals(1.0, pred.homeProb + pred.drawProb + pred.awayProb, 0.001)
        // Avantage domicile : P(home) > P(away) à Elo égaux
        assertTrue(pred.homeProb > pred.awayProb)
    }

    @Test
    fun `home win raises home elo and lowers away elo`() {
        val (newHome, newAway) = useCase.update(1500.0, 1500.0, 2, 0)
        assertTrue(newHome > 1500.0)
        assertTrue(newAway < 1500.0)
        // Somme conservée (système à somme nulle)
        assertEquals(3000.0, newHome + newAway, 0.001)
    }

    @Test
    fun `draw moves ratings toward each other`() {
        val (newHome, newAway) = useCase.update(1600.0, 1400.0, 1, 1)
        assertTrue(newHome < 1600.0)
        assertTrue(newAway > 1400.0)
    }

    @Test
    fun `upset moves more points than expected win`() {
        val (_, upsetAway) = useCase.update(1600.0, 1400.0, 0, 1)
        val (_, expectedAway) = useCase.update(1400.0, 1600.0, 0, 1)
        assertTrue((upsetAway - 1400.0) > (expectedAway - 1600.0))
    }
}
