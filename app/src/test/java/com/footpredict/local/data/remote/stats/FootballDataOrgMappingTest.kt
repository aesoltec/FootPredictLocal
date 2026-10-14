package com.footpredict.local.data.remote.stats

import com.footpredict.local.data.remote.fixture.FdCompetitions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FootballDataOrgMappingTest {

    @Test
    fun `league codes cover backend leagues`() {
        assertEquals("PL", FdCompetitions.codeFor(39))
        assertEquals("PD", FdCompetitions.codeFor(140))
        assertEquals("SA", FdCompetitions.codeFor(135))
        assertEquals("BL1", FdCompetitions.codeFor(78))
        assertEquals("FL1", FdCompetitions.codeFor(61))
        assertEquals("CL", FdCompetitions.codeFor(2))
        assertNull(FdCompetitions.codeFor(9999))
    }

    @Test
    fun `standing row splits totals evenly`() {
        val row = FdStandingRow(
            team = FdStandingTeam(id = 57, name = "Arsenal FC"),
            playedGames = 8, won = 6, draw = 1, lost = 1,
            goalsFor = 20, goalsAgainst = 7, form = "W,W,D,W,W"
        )
        val entity = row.toEntity(leagueId = 39, season = 2026, now = 999L)
        assertEquals(57, entity.teamId)
        assertEquals(8, entity.playedTotal)
        assertEquals(4, entity.playedHome)
        assertEquals(4, entity.playedAway)
        assertEquals(10, entity.goalsForHome)
        assertEquals(10, entity.goalsForAway)
        assertEquals("W,W,D,W,W", entity.form)
        assertEquals(999L, entity.lastUpdated)
    }
}
