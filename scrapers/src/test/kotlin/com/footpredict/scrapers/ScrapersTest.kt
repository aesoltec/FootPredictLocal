package com.footpredict.scrapers

import com.footpredict.scrapers.impl.FootballDataUkScraper
import com.footpredict.scrapers.impl.StatsBombScraper
import com.footpredict.scrapers.impl.SoccerStatsScraper
import com.footpredict.scrapers.impl.UnderstatScraper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ParsersTest {

    @Test
    fun `csv split respects quotes`() {
        assertEquals(
            listOf("a", "b,c", "d"),
            FootballDataUkScraper.splitLine("a,\"b,c\",d")
        )
    }

    @Test
    fun `csv skips blanks`() {
        val rows = FootballDataUkScraper.parseCsv("Date,HomeTeam\n\n2025-08-15,Arsenal\n")
        assertEquals(2, rows.size)
    }

    @Test
    fun `statsbomb splitter isolates objects`() {
        val json = """[{"match_id": 1, "home_team": {"home_team_name": "A"}}, {"match_id": 2}]"""
        val parts = StatsBombScraper.splitTopLevelObjects(json)
        assertEquals(2, parts.size)
        assertEquals("A", StatsBombScraper.extractNested(parts[0], "home_team", "home_team_name"))
        assertEquals("2", StatsBombScraper.extract(parts[1], "match_id"))
    }

    @Test
    fun `understat date parses`() {
        assertEquals(
            1755284400L,
            UnderstatScraper.parseUnderstatDate("2025-08-15 19:00:00")
        )
        assertEquals(0L, UnderstatScraper.parseUnderstatDate("n'importe quoi"))
    }

    @Test
    fun `soccerstats row parsing`() {
        val row = "<tr class=\"trow1\"><td>Arsenal</td><td>10</td><td>7</td><td>2</td><td>1</td><td>22</td><td>9</td></tr>"
        val found = SoccerStatsScraper.findTeamRow("<table>$row</table>", "arsenal")
        assertNotNull(found)
        val stats = SoccerStatsScraper.rowToStats(found!!)!!
        assertEquals(10, stats.played)
        assertEquals(22, stats.goalsFor)
        assertNull(SoccerStatsScraper.rowToStats("<tr><td>x</td></tr>"))
    }

    @Test
    fun `match keys normalize and parse`() {
        assertEquals("manchester united", MatchKeys.normalize("Manchester United!"))
        val triple = MatchKeys.parse("Arsenal|Chelsea|2025-08-15")!!
        assertEquals("Arsenal", triple.first)
        assertNull(MatchKeys.parse("invalide"))
        assertTrue(MatchKeys.parseDateToEpoch("2025-08-15") > 0)
    }
}

class OrchestratorTest {

    private class FakeScraper(
        override val sourceName: String,
        override val reliability: Double,
        private val pick: Outcome?,
        private val fail: Boolean = false,
        override val feasibility: Feasibility = Feasibility.SUPPORTED
    ) : FootballScraper {
        override suspend fun fetchData(matchId: String): MatchData? {
            if (fail) throw RuntimeException("boom")
            kotlinx.coroutines.delay(50)
            return MatchData("A", "B", source = sourceName)
        }

        override suspend fun fetchPrediction(matchId: String): Prediction? =
            pick?.let { Prediction(it, 0.6, source = sourceName, weight = reliability) }
    }

    @Test
    fun `failures isolated and blocked skipped`() = runTest {
        val orchestrator = ScrapingOrchestrator(perSiteTimeoutMs = 5000)
        val scrapers = listOf(
            FakeScraper("ok1", 0.9, Outcome.HOME),
            FakeScraper("ok2", 0.8, Outcome.HOME),
            FakeScraper("ko", 0.9, null, fail = true),
            FakeScraper("blocked", 1.0, Outcome.AWAY, feasibility = Feasibility.BLOCKED)
        )
        val results = orchestrator.scrapeAll("A|B|2025-08-15", scrapers)
        assertEquals(3, results.size) // bloqué exclu
        assertEquals(2, results.count { it.succeeded })
        val final = PredictionEngine().aggregate(results)!!
        assertEquals(Outcome.HOME, final.outcome)
    }

    @Test
    fun `slow site does not block others`() = runTest {
        val slow = object : FootballScraper {
            override val sourceName = "slow"
            override val reliability = 0.9
            override val feasibility = Feasibility.SUPPORTED
            override suspend fun fetchData(matchId: String): MatchData? {
                kotlinx.coroutines.delay(60_000)
                return null
            }
        }
        val orchestrator = ScrapingOrchestrator(perSiteTimeoutMs = 200)
        val results = orchestrator.scrapeAll(
            "A|B|2025-08-15",
            listOf(slow, FakeScraper("fast", 0.5, Outcome.DRAW))
        )
        assertEquals(2, results.size)
        assertTrue(results.any { it.source == "fast" && it.succeeded })
    }
}
