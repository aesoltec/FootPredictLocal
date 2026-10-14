package com.footpredict.local.data.notification

import com.footpredict.local.domain.model.EnrichedMatch
import com.footpredict.local.domain.model.EnsemblePrediction
import com.footpredict.local.domain.model.Match
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerFilterTest {

    private fun match(league: Int = 39, status: String = "NS") = EnrichedMatch(
        fixture = Match(
            id = 1, leagueId = league, season = 2026, round = null,
            dateTimestamp = 1_791_631_800L, statusShort = status, statusLong = null,
            homeTeamId = 1, homeTeamName = "A", homeTeamLogo = null,
            awayTeamId = 2, awayTeamName = "B", awayTeamLogo = null,
            goalsHome = null, goalsAway = null, venueName = null, venueCity = null
        ),
        homeStats = null, awayStats = null, odds = null,
        eloHome = null, eloAway = null, sources = emptyList(), conflicts = emptyList()
    )

    private fun pred(confidence: Double, edge: Double?) = EnsemblePrediction(
        matchId = 1, homeWinProb = 0.5, drawProb = 0.3, awayWinProb = 0.2,
        over25Prob = 0.5, confidence = confidence,
        divergenceLevel = com.footpredict.local.domain.model.DivergenceLevel.LOW,
        dixonColes = null, market = null, elo = null,
        valueBets = if (edge == null) emptyList() else listOf(
            com.footpredict.local.domain.model.ValueBet("HOME", edge, 0.6, 0.45)
        )
    )

    @Test
    fun `passes only when all cumulative conditions hold`() {
        val ok = TriggerFilter.passes(match(), pred(0.75, 0.20))
        assertTrue(ok)
    }

    @Test
    fun `rejects wrong league finished low confidence or weak edge`() {
        assertFalse(TriggerFilter.passes(match(league = 88), pred(0.75, 0.20)))
        assertFalse(TriggerFilter.passes(match(status = "FT"), pred(0.75, 0.20)))
        assertFalse(TriggerFilter.passes(match(), pred(0.60, 0.20)))
        assertFalse(TriggerFilter.passes(match(), pred(0.75, 0.10)))
        assertFalse(TriggerFilter.passes(match(), pred(0.75, null)))
        assertFalse(TriggerFilter.passes(match(), null))
    }
}
