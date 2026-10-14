package com.footpredict.local.domain.usecase

import com.footpredict.local.domain.model.Match
import com.footpredict.local.domain.model.RecentMatch
import com.footpredict.local.domain.model.TeamStats
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitaires — Section 15 du prompt Dixon-Coles.
 */
class CalculateDixonColesUseCaseTest {

    private val useCase = CalculateDixonColesUseCase()

    private fun sampleMatch() = Match(
        id = 1, leagueId = 39, season = 2025, round = "MD 1",
        dateTimestamp = 1_700_000_000L, statusShort = "NS", statusLong = null,
        homeTeamId = 33, homeTeamName = "Man Utd", homeTeamLogo = null,
        awayTeamId = 40, awayTeamName = "Liverpool", awayTeamLogo = null,
        goalsHome = null, goalsAway = null, venueName = null, venueCity = null
    )

    private fun stats(
        teamId: Int, name: String,
        gfH: Int, gfA: Int, gaH: Int, gaA: Int,
        played: Int = 10
    ) = TeamStats(
        teamId = teamId, teamName = name, teamLogo = null,
        leagueId = 39, season = 2025,
        playedHome = played / 2, playedAway = played / 2, playedTotal = played,
        goalsForHome = gfH, goalsForAway = gfA,
        goalsAgainstHome = gaH, goalsAgainstAway = gaA
    )

    @Test
    fun `probabilities sum to ~1`() = runTest {
        val pred = useCase(
            sampleMatch(),
            stats(33, "Man Utd", 12, 8, 6, 9),
            stats(40, "Liverpool", 15, 11, 5, 7)
        )
        val sum = pred.homeWinProb + pred.drawProb + pred.awayWinProb
        assertEquals(1.0, sum, 0.02)
        assertTrue(pred.over25Prob in 0.0..1.0)
        assertTrue(pred.under25Prob in 0.0..1.0)
        assertEquals(1.0, pred.over25Prob + pred.under25Prob, 0.02)
    }

    @Test
    fun `stronger home attack raises home win probability`() = runTest {
        val weak = useCase(
            sampleMatch(),
            stats(33, "Weak", 4, 3, 10, 12),
            stats(40, "Strong", 14, 12, 4, 5)
        )
        val strong = useCase(
            sampleMatch(),
            stats(33, "StrongHome", 18, 14, 4, 5),
            stats(40, "WeakAway", 5, 4, 12, 14)
        )
        assertTrue(
            "Strong home attack should yield higher P(home)",
            strong.homeWinProb > weak.homeWinProb
        )
    }

    @Test
    fun `lambdas are positive`() = runTest {
        val pred = useCase(
            sampleMatch(),
            stats(1, "A", 10, 8, 7, 8),
            stats(2, "B", 9, 9, 8, 7)
        )
        assertTrue(pred.lambdaHome > 0)
        assertTrue(pred.lambdaAway > 0)
    }

    @Test
    fun `recent form differentiates otherwise identical teams`() = runTest {
        val home = stats(33, "Man Utd", 4, 3, 10, 12, played = 4)
        val away = stats(40, "Liverpool", 4, 3, 10, 12, played = 4)
        val hotStreak = List(5) { RecentMatch(goalsFor = 3, goalsAgainst = 0, daysAgo = it * 7.0, wasHome = true) }
        val coldStreak = List(5) { RecentMatch(goalsFor = 0, goalsAgainst = 3, daysAgo = it * 7.0, wasHome = false) }
        val hot = useCase(sampleMatch(), home, away, homeRecent = hotStreak, awayRecent = coldStreak)
        val neutral = useCase(sampleMatch(), home, away)
        assertTrue(hot.homeWinProb > neutral.homeWinProb)
        assertTrue(hot.awayWinProb < neutral.awayWinProb)
    }

    @Test
    fun `time decay weights recent matches more`() = runTest {        val home = stats(33, "Man Utd", 4, 3, 10, 12, played = 4)
        val away = stats(40, "Liverpool", 4, 3, 10, 12, played = 4)
        // Même buts totaux, ordre inversé : le bon match est récent vs ancien.
        val recentGood = listOf(
            RecentMatch(goalsFor = 4, goalsAgainst = 0, daysAgo = 3.0, wasHome = true),
            RecentMatch(goalsFor = 0, goalsAgainst = 1, daysAgo = 300.0, wasHome = true)
        )
        val oldGood = listOf(
            RecentMatch(goalsFor = 0, goalsAgainst = 1, daysAgo = 3.0, wasHome = true),
            RecentMatch(goalsFor = 4, goalsAgainst = 0, daysAgo = 300.0, wasHome = true)
        )
        val recent = useCase(sampleMatch(), home, away, homeRecent = recentGood)
        val old = useCase(sampleMatch(), home, away, homeRecent = oldGood)
        assertTrue(recent.lambdaHome > old.lambdaHome)
    }

    @Test
    fun `btts probabilities sum to 1 and rise with attacking strength`() = runTest {
        val pred = useCase(
            sampleMatch(),
            stats(33, "Man Utd", 12, 8, 6, 9),
            stats(40, "Liverpool", 15, 11, 5, 7)
        )
        assertEquals(1.0, pred.bttsYesProb + pred.bttsNoProb, 0.02)
        assertTrue(pred.bttsYesProb in 0.0..1.0)
        val cagey = useCase(
            sampleMatch(),
            stats(1, "A", 2, 1, 3, 2, played = 10),
            stats(2, "B", 1, 2, 2, 3, played = 10)
        )
        assertTrue(pred.bttsYesProb > cagey.bttsYesProb)
    }

    @Test
    fun `btts penalty reduces yes by 15 percent`() = runTest {
        // Recalibrage 2026-10-13 : facteur 0,85 explicite.
        assertEquals(0.85, CalculateDixonColesUseCase.BTTS_XG_PENALTY, 0.0)
        val pred = useCase(
            sampleMatch(),
            stats(33, "Man Utd", 12, 8, 6, 9),
            stats(40, "Liverpool", 15, 11, 5, 7)
        )
        assertTrue(pred.bttsYesProb in 0.0..1.0)
        assertEquals(1.0, pred.bttsYesProb + pred.bttsNoProb, 0.02)
    }
}
