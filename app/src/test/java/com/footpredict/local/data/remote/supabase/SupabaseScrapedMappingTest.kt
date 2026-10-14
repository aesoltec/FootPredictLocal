package com.footpredict.local.data.remote.supabase

import com.footpredict.local.data.remote.fixture.ScrapedFixtureRowDto
import com.footpredict.local.data.remote.fixture.toEntity
import com.footpredict.local.data.remote.odds.ScrapedOddsRowDto
import com.footpredict.local.data.remote.odds.toEntity
import com.footpredict.local.data.remote.stats.ScrapedTeamStatsRowDto
import com.footpredict.local.data.remote.stats.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Mappers purs des tables Supabase (aucun réseau requis).
 */
class SupabaseScrapedMappingTest {

    @Test
    fun `fixture row maps to MatchEntity`() {
        val row = ScrapedFixtureRowDto(
            matchId = 123, leagueId = 39, season = 2025, round = null,
            dateTs = 1_700_000_000L, statusShort = "FT",
            homeTeamId = 111, homeTeamName = "Arsenal",
            awayTeamId = 222, awayTeamName = "Chelsea",
            goalsHome = 2, goalsAway = 1
        )
        val entity = row.toEntity(now = 999L)
        assertEquals(123, entity.id)
        assertEquals(39, entity.leagueId)
        assertEquals("FT", entity.statusShort)
        assertEquals(111, entity.homeTeamId)
        assertEquals("Chelsea", entity.awayTeamName)
        assertEquals(2, entity.goalsHome)
        assertEquals(1, entity.goalsAway)
        assertEquals(999L, entity.lastUpdated)
    }

    @Test
    fun `scheduled fixture keeps null scores`() {
        val row = ScrapedFixtureRowDto(
            matchId = 7, leagueId = 61, season = 2025, round = null,
            dateTs = 1_800_000_000L, statusShort = "NS",
            homeTeamId = 1, homeTeamName = "PSG",
            awayTeamId = 2, awayTeamName = "OM"
        )
        val entity = row.toEntity()
        assertEquals("NS", entity.statusShort)
        assertNull(entity.goalsHome)
        assertNull(entity.goalsAway)
    }

    @Test
    fun `team stats row aggregates totals`() {
        val row = ScrapedTeamStatsRowDto(
            unifiedTeamId = 555, teamName = "Arsenal",
            leagueId = 39, season = 2025,
            playedHome = 5, playedAway = 4,
            gfHome = 12, gfAway = 7, gaHome = 3, gaAway = 6
        )
        val entity = row.toEntity()
        assertEquals(555, entity.teamId)
        assertEquals(9, entity.playedTotal)
        assertEquals(12, entity.goalsForHome)
        assertEquals(7, entity.goalsForAway)
        assertEquals(3, entity.goalsAgainstHome)
        assertEquals(6, entity.goalsAgainstAway)
    }

    @Test
    fun `odds row maps all markets`() {
        val row = ScrapedOddsRowDto(
            matchId = 42, bookmaker = "Pinnacle",
            homeOdds = 1.9, drawOdds = 3.6, awayOdds = 4.2,
            over25 = 2.1, under25 = 1.75, lastUpdated = 123L
        )
        val entity = row.toEntity("oddsportal-supabase")
        assertEquals(42, entity.matchId)
        assertEquals(1.9, entity.homeOdds!!, 0.001)
        assertEquals(1.75, entity.under25Odds!!, 0.001)
        assertEquals("oddsportal-supabase", entity.source)
    }
}
