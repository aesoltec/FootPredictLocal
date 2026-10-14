package com.footpredict.scrapers

import com.footpredict.scrapers.impl.FootballDataOrgScraper
import com.footpredict.scrapers.impl.FootballDataScraper
import com.footpredict.scrapers.impl.FotMobScraper
import com.footpredict.scrapers.impl.OddsApiScraper
import com.footpredict.scrapers.impl.UnderstatScraper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ApiV2Test {

    @Test
    fun `aliases unify variants`() {
        assertEquals("leeds", TeamAliases.canonical("Leeds United"))
        assertEquals("bayern munich", TeamAliases.canonical("FC Bayern München"))
        assertEquals("brighton", TeamAliases.canonical("Brighton & Hove Albion"))
        assertEquals("arsenal", TeamAliases.canonical("Arsenal"))
        assertEquals("cologne", TeamAliases.canonical("1. FC Köln"))
        assertEquals("borussia m gladbach", TeamAliases.canonical("Borussia Mönchengladbach"))
    }

    @Test
    fun `psg variants designate the same entity`() {
        val canon = TeamAliases.canonical("Paris Saint-Germain")
        assertEquals(canon, TeamAliases.canonical("PSG"))
        assertEquals(canon, TeamAliases.canonical("Paris SG"))
        assertEquals(canon, TeamAliases.canonical("  paris-saint-germain "))
    }

    @Test
    fun `metadata builds universal key`() {
        assertEquals(
            "Arsenal|Chelsea|2025-08-15",
            MatchMetadata("Arsenal", "Chelsea", "2025-08-15").toMatchId()
        )
    }

    @Test
    fun `implied probabilities remove margin`() {
        val (h, d, a) = FootballDataScraper.impliedProbabilities(2.0, 3.5, 4.0)!!
        assertEquals(1.0, h + d + a, 0.001)
        assertTrue(h > d && d > a)
        assertNull(FootballDataScraper.impliedProbabilities(1.0, 3.5, 4.0))
        assertNull(FootballDataScraper.impliedProbabilities(null, 3.5, 4.0))
    }

    @Test
    fun `trend score sums to one across outcomes`() = runTest {
        val meta = MatchMetadata("A", "B", "2025-08-15")
        val preds = listOf(
            ScraperPrediction("s1", Outcome.HOME, 0.6, weight = 0.9),
            ScraperPrediction("s2", Outcome.HOME, 0.55, weight = 0.8),
            ScraperPrediction("s3", Outcome.AWAY, 0.5, weight = 0.7)
        )
        val agg = PredictionEngine().aggregatePredictions(meta, preds)!!
        assertEquals(Outcome.HOME, agg.outcome)
        assertEquals(1.7 / 2.4, agg.confidence, 0.001)
        assertEquals(1.0, agg.trendByOutcome.values.sum(), 0.001)
        assertEquals(0.7 / 2.4, agg.trendByOutcome[Outcome.AWAY]!!, 0.001)
        assertEquals(0.0, agg.trendByOutcome[Outcome.DRAW]!!, 0.0)
        assertTrue(agg.warnings.isEmpty()) // 3 contributeurs, consensus net
    }

    @Test
    fun `empty predictions return null`() = runTest {
        val meta = MatchMetadata("A", "B", "2025-08-15")
        assertNull(PredictionEngine().aggregatePredictions(meta, emptyList()))
    }

    @Test
    fun `market consensus gives percent indices and filters failures`() = runTest {
        val meta = MatchMetadata("PSG", "OM", "2026-10-17")
        val preds: List<ScraperPrediction?> = listOf(
            null,
            ScraperPrediction("s1", Outcome.HOME, 0.6, over25Prob = 0.7, weight = 0.9),
            ScraperPrediction("s2", Outcome.HOME, 0.55, over25Prob = 0.6, weight = 0.8),
            ScraperPrediction("s3", Outcome.AWAY, 0.5, over25Prob = 0.2, weight = 0.7),
            ScraperPrediction("bad", Outcome.DRAW, 1.5, weight = 0.9), // confiance invalide
            ScraperPrediction("zero", Outcome.DRAW, 0.4, weight = 0.0) // poids nul
        )
        val consensus = PredictionEngine().aggregateMarkets(meta, preds)!!
        // 1X2 : HOME = (0.9+0.8)/2.4 = 70.8 %
        assertEquals(Outcome.HOME, consensus.outcome1X2.side)
        assertEquals(70.83, consensus.outcome1X2.confidencePct, 0.01)
        // Over : (0.7*0.9+0.6*0.8+0.2*0.7)/2.4 = 0.5208 → OVER 52.08 %
        assertEquals(PredictionEngine.OverUnderSide.OVER, consensus.overUnder!!.side)
        assertEquals(52.08, consensus.overUnder.confidencePct, 0.01)
        assertEquals(listOf("s1", "s2", "s3"), consensus.sourcesUsed)
        assertTrue(consensus.warnings.isEmpty())
    }

    @Test
    fun `no over-under data yields null market`() = runTest {
        val meta = MatchMetadata("A", "B", "2025-08-15")
        val preds = listOf(ScraperPrediction("s1", Outcome.DRAW, 0.4, weight = 0.8))
        val consensus = PredictionEngine().aggregateMarkets(meta, preds)!!
        assertNull(consensus.overUnder)
        assertTrue(consensus.warnings.any { it.startsWith("signal-faible") })
    }

    @Test
    fun `bookmaker odds refine the final index`() = runTest {
        val meta = MatchMetadata("PSG", "OM", "2026-10-17")
        // Sans marché : 3 avis dispersés, vainqueur à 41.7 % → consensus mou.
        val statsOnly = listOf(
            ScraperPrediction("dc", Outcome.HOME, 0.5, weight = 0.5),
            ScraperPrediction("elo", Outcome.AWAY, 0.5, weight = 0.4),
            ScraperPrediction("x", Outcome.DRAW, 0.4, weight = 0.3)
        )
        val before = PredictionEngine().aggregateMarkets(meta, statsOnly)!!
        assertTrue(before.warnings.contains("consensus-mou"))
        // Avec marché fiable pro-HOME : l’indice monte à 65.9 %, l’avertissement tombe.
        val withMarket = statsOnly + ScraperPrediction(
            "the-odds-api", Outcome.HOME, 0.62, over25Prob = 0.58, weight = 0.85
        )
        val after = PredictionEngine().aggregateMarkets(meta, withMarket)!!
        assertEquals(Outcome.HOME, after.outcome1X2.side)
        assertEquals(65.85, after.outcome1X2.confidencePct, 0.01)
        assertEquals(PredictionEngine.OverUnderSide.OVER, after.overUnder!!.side)
        assertTrue(after.warnings.none { it == "consensus-mou" })
    }

    @Test
    fun `football-data standings parse`() {
        val body = """{"standings": [{"table": [
            {"team": {"name": "Paris Saint-Germain"}, "playedGames": 8, "won": 6,
             "drawn": 1, "lost": 1, "goalsFor": 20, "goalsAgainst": 7, "form": "W,W,D,W,W"}
        ]}]}"""
        val stats = FootballDataOrgScraper.parseStandings(body)
        val psg = stats["paris saint germain"]!!
        assertEquals(8, psg.played)
        assertEquals(20, psg.goalsFor)
        assertEquals(listOf('W', 'W', 'D', 'W', 'W'), psg.form)
    }

    @Test
    fun `throttle guard trips on exhausted quota`() {
        FootballDataOrgScraper.checkThrottle(mapOf("X-Requests-Available-Minute" to listOf("8")))
        try {
            FootballDataOrgScraper.checkThrottle(mapOf("x-requests-available-minute" to listOf("1")))
            fail("aurait dû lever QuotaExhaustedException")
        } catch (e: FootballDataOrgScraper.QuotaExhaustedException) {
            assertEquals(1, e.available)
        }
        // Headers absents : on laisse passer (pas de preuve de dépassement).
        FootballDataOrgScraper.checkThrottle(emptyMap())
    }

    @Test
    fun `odds events parse h2h and totals`() {
        val body = """[{
            "home_team": "Arsenal", "away_team": "Chelsea",
            "commence_time": "2026-10-17T11:30:00Z",
            "bookmakers": [{"title": "Pinnacle", "markets": [
                {"key": "h2h", "outcomes": [
                    {"name": "Arsenal", "price": 2.1},
                    {"name": "Draw", "price": 3.4},
                    {"name": "Chelsea", "price": 3.5}]},
                {"key": "totals", "outcomes": [
                    {"name": "Over", "price": 2.0, "point": 2.5},
                    {"name": "Under", "price": 1.85, "point": 2.5},
                    {"name": "Over", "price": 5.0, "point": 3.5}]}
            ]}]}]"""
        val events = OddsApiScraper.parseEvents(body)
        assertEquals(1, events.size)
        val e = events.first()
        assertEquals(2.1, e.home!!, 0.001)
        assertEquals(2.0, e.over25!!, 0.001)
        assertEquals(1.85, e.under25!!, 0.001)
        assertEquals("Pinnacle", e.bookmaker)
        assertEquals(3, OddsApiScraper.parseQuotaRemaining(mapOf("x-requests-remaining" to listOf("3"))))
        assertEquals(-1, OddsApiScraper.parseQuotaRemaining(emptyMap()))
    }

    @Test
    fun `runner collects successes and drops failures`() = runTest {
        val ok = object : FootballScraper {
            override val sourceName = "ok"
            override val reliability = 0.9
            override val feasibility = Feasibility.SUPPORTED
            override suspend fun fetchData(matchId: String) = null
            override suspend fun predict(metadata: MatchMetadata) =
                ScraperPrediction("ok", Outcome.DRAW, 0.4, weight = 0.9)
        }
        val ko = object : FootballScraper {
            override val sourceName = "ko"
            override val reliability = 0.9
            override val feasibility = Feasibility.SUPPORTED
            override suspend fun fetchData(matchId: String): MatchData? =
                throw RuntimeException("403")
            override suspend fun predict(metadata: MatchMetadata): ScraperPrediction? =
                throw RuntimeException("timeout")
        }
        val out = ScraperRunner().runAll(MatchMetadata("A", "B", "2025-08-15"), listOf(ok, ko))
        assertEquals(1, out.size)
        assertEquals(Outcome.DRAW, out.first().outcome)
    }

    @Test
    fun `legacy script regex decodes understat escapes`() {
        val html = """<script>var datesData = JSON.parse('[{"h":\x7Btest\x7D}]');</script>"""
        val decoded = UnderstatScraper.extractLegacyDatesData(html)!!
        assertTrue(decoded.contains("{test}"))
        assertNull(UnderstatScraper.extractLegacyDatesData("<html>sans data</html>"))
    }

    @Test
    fun `fotmob sample payload parses to prediction`() {
        val sample = """{"homeTeam": {"name": "Arsenal"}, "awayTeam": {"name": "Chelsea"},
            "homeOdds": "2.10", "drawOdds": "3.40", "awayOdds": "3.50"}"""
        val pred = FotMobScraper.parsePrediction(sample)!!
        assertEquals(Outcome.HOME, pred.outcome)
        assertEquals("fotmob", pred.source)
        assertNull(FotMobScraper.parsePrediction("""{"homeTeam": {"name": "X"}}"""))
    }
}
