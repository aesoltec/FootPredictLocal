package com.footpredict.local.data.aggregator

import com.footpredict.local.data.local.entity.MatchEntity
import com.footpredict.local.data.local.entity.TeamStatsEntity
import com.footpredict.local.data.remote.fixture.FixtureRemoteDataSource
import com.footpredict.local.data.remote.stats.StatsRemoteDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Non-régression : une source fiable mais vide (clé API manquante) ne doit
 * jamais masquer une source moins fiable mais non vide (scraping).
 */
class DataFusionAggregatorTest {

    private class FakeFixture(
        override val sourceName: String,
        override val reliabilityScore: Double,
        private val data: List<MatchEntity>
    ) : FixtureRemoteDataSource {
        override suspend fun fetchFixtures(leagueId: Int, season: Int): List<MatchEntity> = data
    }

    private fun match(id: Int) = MatchEntity(
        id = id, externalIdsJson = null, leagueId = 39, season = 2026,
        round = null, dateTimestamp = 1_791_631_800L, statusShort = "NS",
        statusLong = null, homeTeamId = 1, homeTeamName = "Arsenal",
        homeTeamLogo = null, awayTeamId = 2, awayTeamName = "Leeds",
        awayTeamLogo = null, goalsHome = null, goalsAway = null,
        venueName = null, venueCity = null, sourcesJson = null,
        conflictsJson = null, lastUpdated = 0L
    )

    private fun aggregator(vararg sources: FixtureRemoteDataSource) =
        DataFusionAggregator(sources.toSet(), emptySet(), emptySet())

    @Test
    fun `empty reliable source does not mask non-empty scraping source`() = runTest {
        val agg = aggregator(
            FakeFixture("api-football", 0.8, emptyList()),
            FakeFixture("football-data.org", 0.75, emptyList()),
            FakeFixture("understat-supabase", 0.6, listOf(match(1), match(2)))
        )
        val (fixtures, sources) = agg.fetchMergedFixtures(39, 2026)
        assertEquals(2, fixtures.size)
        assertTrue(sources.contains("understat-supabase"))
    }

    @Test
    fun `most reliable non-empty source wins`() = runTest {
        val agg = aggregator(
            FakeFixture("api-football", 0.8, listOf(match(9))),
            FakeFixture("understat-supabase", 0.6, listOf(match(1), match(2)))
        )
        val (fixtures, _) = agg.fetchMergedFixtures(39, 2026)
        assertEquals(listOf(9), fixtures.map { it.id })
    }

    @Test
    fun `all empty sources yield empty without crashing`() = runTest {
        // Audit 2026-10-14 : hors-ligne + cache vide → liste vide honnête,
        // pas de NPE sur maxByOrNull!! (ancien crash au lancement).
        val agg = aggregator(
            FakeFixture("api-football", 0.8, emptyList()),
            FakeFixture("football-data.org", 0.75, emptyList())
        )
        val (fixtures, sources) = agg.fetchMergedFixtures(39, 2026)
        assertTrue(fixtures.isEmpty())
        assertTrue(sources.isEmpty())
    }

    private fun named(id: Int, home: String, away: String, dateTs: Long = 1_791_631_800L) =
        match(id).copy(homeTeamName = home, awayTeamName = away, dateTimestamp = dateTs)

    @Test
    fun `same fixture across name variants`() {
        // Doublons constatés sur téléphone : « Hull » == « Hull City AFC ».
        assertTrue(isSameFixture(named(1, "Hull", "Fulham"), named(2, "Hull City AFC", "Fulham FC")))
        assertTrue(
            isSameFixture(
                named(1, "Brighton", "Crystal Palace"),
                named(2, "Brighton & Hove Albion FC", "Crystal Palace FC")
            )
        )
        assertTrue(isSameFixture(named(1, "Everton", "Chelsea"), named(2, "Everton FC", "Chelsea FC")))
    }

    @Test
    fun `different teams or dates are not same fixture`() {
        assertTrue(!isSameFixture(named(1, "Arsenal", "Leeds"), named(2, "Arsenal", "Chelsea")))
        // Aller/retour à 3 mois d’intervalle : deux vrais matchs.
        assertTrue(
            !isSameFixture(
                named(1, "Arsenal", "Leeds", 1_791_631_800L),
                named(2, "Leeds", "Arsenal", 1_791_631_800L + 90 * 86400L)
            )
        )
    }

    @Test
    fun `dedupe keeps first occurrence only`() {
        val rows = listOf(
            named(1, "Hull City AFC", "Fulham FC"),
            named(2, "Hull", "Fulham"),
            named(3, "Arsenal", "Leeds")
        )
        assertEquals(listOf(1, 3), dedupeFixtures(rows).map { it.id })
    }

    @Test
    fun `all empty returns empty`() = runTest {
        val agg = aggregator(
            FakeFixture("api-football", 0.8, emptyList()),
            FakeFixture("understat-supabase", 0.6, emptyList())
        )
        val (fixtures, _) = agg.fetchMergedFixtures(39, 2026)
        assertTrue(fixtures.isEmpty())
    }

    @Test
    fun `league batch prefers highest reliable non-empty source`() = runTest {        val empty = object : StatsRemoteDataSource {
            override val sourceName = "api-football"
            override val reliabilityScore = 0.8
            override suspend fun fetchTeamStats(teamId: Int, leagueId: Int, season: Int) = null
        }
        val full = object : StatsRemoteDataSource {
            override val sourceName = "understat-stats-supabase"
            override val reliabilityScore = 0.65
            override suspend fun fetchTeamStats(teamId: Int, leagueId: Int, season: Int) = null
            override suspend fun fetchLeagueStats(leagueId: Int, season: Int) = listOf(
                TeamStatsEntity(
                    teamId = 1, teamName = "Arsenal", teamLogo = null,
                    leagueId = leagueId, season = season, lastUpdated = 1L
                )
            )
        }
        val agg = DataFusionAggregator(emptySet(), setOf(empty, full), emptySet())
        val rows = agg.fetchLeagueStatsBatch(39, 2026)
        assertEquals(listOf("Arsenal"), rows.map { it.teamName })
    }

    @Test
    fun `mergeXg fills missing xg without touching scores`() {
        val winner = match(1).copy(
            homeTeamName = "Arsenal FC", awayTeamName = "Chelsea FC",
            dateTimestamp = 1_791_631_800L, goalsHome = 2, goalsAway = 1,
            xgHome = null, xgAway = null
        )
        val alt = match(99).copy(
            homeTeamName = "Arsenal", awayTeamName = "Chelsea",
            dateTimestamp = 1_791_631_800L, goalsHome = 9, goalsAway = 9,
            xgHome = 2.3, xgAway = 1.5
        )
        val far = match(100).copy(
            homeTeamName = "Arsenal", awayTeamName = "Chelsea",
            dateTimestamp = 1_791_631_800L + 30 * 86400, xgHome = 5.0, xgAway = 5.0
        )
        val merged = mergeXg(listOf(winner), listOf(alt, far))
        assertEquals(2.3, merged[0].xgHome!!, 0.001)
        assertEquals(1.5, merged[0].xgAway!!, 0.001)
        assertEquals(2, merged[0].goalsHome)
        assertEquals(1, merged[0].goalsAway)
    }

    @Test
    fun `mergeXg keeps existing xg and ignores empty alternates`() {
        val winner = match(1).copy(xgHome = 1.0, xgAway = 1.0)
        assertEquals(1.0, mergeXg(listOf(winner), emptyList())[0].xgHome!!, 0.0)
    }

    @Test
    fun `mergeXg matches official names against short names`() {
        // Cas réel : football-data.org vs Understat.
        val winner = match(1).copy(
            homeTeamName = "AFC Bournemouth", awayTeamName = "Liverpool FC",
            dateTimestamp = 1_791_631_800L, xgHome = null, xgAway = null
        )
        val alt = match(99).copy(
            homeTeamName = "Bournemouth", awayTeamName = "Liverpool",
            dateTimestamp = 1_791_631_800L, xgHome = 0.94, xgAway = 1.29
        )
        val merged = mergeXg(listOf(winner), listOf(alt))
        assertEquals(0.94, merged[0].xgHome!!, 0.001)
        assertEquals(1.29, merged[0].xgAway!!, 0.001)
    }
}
