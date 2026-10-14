package com.footpredict.local.presentation.screen.matchlist

import com.footpredict.local.domain.model.DivergenceLevel
import com.footpredict.local.domain.model.EnrichedMatch
import com.footpredict.local.domain.model.EnsemblePrediction
import com.footpredict.local.domain.model.Match
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sélection pure : top 3 à pronostiquer, top 5 confiance, top 5 à éviter.
 */
class PickHelpersTest {

    private fun match(id: Int, status: String, dateTs: Long, home: String = "A", away: String = "B") =
        EnrichedMatch(
            fixture = Match(
                id = id, leagueId = 39, season = 2026, round = null,
                dateTimestamp = dateTs, statusShort = status, statusLong = null,
                homeTeamId = 1, homeTeamName = home, homeTeamLogo = null,
                awayTeamId = 2, awayTeamName = away, awayTeamLogo = null,
                goalsHome = null, goalsAway = null, venueName = null, venueCity = null
            ),
            homeStats = null, awayStats = null, odds = null,
            eloHome = null, eloAway = null, sources = emptyList(), conflicts = emptyList()
        )

    private fun pred(confidence: Double, divergence: DivergenceLevel = DivergenceLevel.LOW) =
        EnsemblePrediction(
            matchId = 1, homeWinProb = 0.5, drawProb = 0.3, awayWinProb = 0.2,
            over25Prob = 0.5, confidence = confidence, divergenceLevel = divergence,
            dixonColes = null, market = null, elo = null
        )

    @Test
    fun `topUpcoming keeps three soonest NS matches`() {
        val list = listOf(
            match(1, "NS", 300), match(2, "FT", 50), match(3, "NS", 100),
            match(4, "NS", 200), match(5, "NS", 400), match(6, "NS", 500)
        )
        assertEquals(listOf(3, 4, 1), PickHelpers.topUpcoming(list).map { it.fixture.id })
    }

    @Test
    fun `allUpcoming returns every NS match sorted`() {
        val list = listOf(
            match(1, "NS", 300), match(2, "FT", 50), match(3, "NS", 100),
            match(4, "NS", 200), match(5, "NS", 400), match(6, "NS", 500)
        )
        assertEquals(
            listOf(3, 4, 1, 5, 6),
            PickHelpers.allUpcoming(list).map { it.fixture.id }
        )
    }

    @Test
    fun `topConfident ranks by confidence and caps at five`() {
        val list = (1..7).map { match(it, "NS", 100L + it) }
        val preds = list.associate { it.fixture.id to pred(0.10 * it.fixture.id) }
        val top = PickHelpers.topConfident(list, preds)
        assertEquals(5, top.size)
        assertEquals(7, top.first().matchId)
        assertTrue(top.map { it.confidence }.zipWithNext().all { (a, b) -> a >= b })
    }

    @Test
    fun `toAvoid puts divergent first then lowest confidence`() {
        val list = listOf(match(1, "NS", 100), match(2, "NS", 200), match(3, "NS", 300))
        val preds = mapOf(
            1 to pred(0.60),
            2 to pred(0.65, DivergenceLevel.HIGH),
            3 to pred(0.40)
        )
        val avoid = PickHelpers.toAvoid(list, preds)
        assertEquals(2, avoid.first().matchId)
        assertEquals(3, avoid[1].matchId)
    }

    @Test
    fun `toAvoid excludes top picks`() {        val list = listOf(match(1, "NS", 100), match(2, "NS", 200), match(3, "NS", 300))
        val preds = mapOf(
            1 to pred(0.60),
            2 to pred(0.65, DivergenceLevel.HIGH),
            3 to pred(0.40)
        )
        val tops = PickHelpers.topConfident(list, preds)
        val avoid = PickHelpers.toAvoid(list, preds, exclude = tops.map { it.matchId }.toSet())
        assertTrue(avoid.none { it.matchId == 2 })
        assertTrue(avoid.none { it.matchId in tops.map { t -> t.matchId } })
    }

    @Test
    fun `topUpcoming pool parameter is honored`() {
        val list = (1..12).map { match(it, "NS", 100L + it) }
        assertEquals(10, PickHelpers.topUpcoming(list, 10).size)
        assertEquals(3, PickHelpers.topUpcoming(list).size)
    }

    @Test
    fun `double chance covers and best side wins`() {
        val dc = PickHelpers.doubleChanceOf(0.5, 0.3, 0.2)
        assertEquals(0.8, dc.p1X, 0.001)
        assertEquals(0.7, dc.p12, 0.001)
        assertEquals(0.5, dc.pX2, 0.001)
        assertEquals("1X" to 0.8, dc.best())
    }

    @Test
    fun `twelve wins when draw is least likely`() {
        // Match équilibré : le « 12 » (pas de nul) est le vrai meilleur côté.
        val dc = PickHelpers.doubleChanceOf(0.35, 0.30, 0.35)
        assertEquals("12" to 0.70, dc.best())
    }

    @Test
    fun `strength thresholds match spec`() {
        assertEquals(Strength.STRONG, strengthOf(0.71))
        assertEquals(Strength.MEDIUM, strengthOf(0.70))
        assertEquals(Strength.MEDIUM, strengthOf(0.55))
        assertEquals(Strength.LOW, strengthOf(0.549))
    }

    private fun marketPred(over25: Double?, bttsYes: Double?) =
        EnsemblePrediction(
            matchId = 1, homeWinProb = 0.4, drawProb = 0.3, awayWinProb = 0.3,
            over25Prob = over25, confidence = 0.5, divergenceLevel = DivergenceLevel.LOW,
            dixonColes = null, market = null, elo = null, bttsYesProb = bttsYes
        )

    @Test
    fun `market tops rank correctly and cap at two`() {
        val list = (1..4).map { match(it, "NS", 100L + it) }
        val preds = mapOf(
            1 to marketPred(0.80, 0.70),
            2 to marketPred(0.70, 0.90),
            3 to marketPred(0.40, 0.30),
            4 to marketPred(null, null)
        )
        assertEquals(listOf(1, 2), PickHelpers.topOver25(list, preds).map { it.matchId })
        assertEquals(listOf(3, 2), PickHelpers.topUnder25(list, preds).map { it.matchId })
        assertEquals(listOf(2, 1), PickHelpers.topBttsYes(list, preds).map { it.matchId })
        assertEquals(listOf(3, 1), PickHelpers.topBttsNo(list, preds).map { it.matchId })
    }

    @Test
    fun `topOver25 filters below safety threshold 062`() {
        val list = (1..3).map { match(it, "NS", 100L + it) }
        val preds = mapOf(
            1 to marketPred(0.61, 0.5),
            2 to marketPred(0.62, 0.5),
            3 to marketPred(0.80, 0.5)
        )
        // Seuil strict > 0,62 : seuls les > 0,62 passent (id 3).
        assertEquals(listOf(3), PickHelpers.topOver25(list, preds).map { it.matchId })
    }

    @Test
    fun `favorite pairs app and market on the same outcome`() {
        val pred = EnsemblePrediction(
            matchId = 1, homeWinProb = 0.55, drawProb = 0.27, awayWinProb = 0.18,
            over25Prob = 0.5, confidence = 0.6, divergenceLevel = DivergenceLevel.LOW,
            dixonColes = null,
            market = com.footpredict.local.domain.model.MarketPrediction(1, 0.45, 0.3, 0.25),
            elo = null
        )
        val (app, mkt) = favoriteVsMarket(
            pred,
            com.footpredict.local.domain.model.MarketPrediction(1, 0.45, 0.3, 0.25)
        )
        assertEquals(0.55, app!!, 0.001)
        assertEquals(0.45, mkt!!, 0.001)
        val (noApp, noMkt) = favoriteVsMarket(null, null)
        assertEquals(null, noApp)
        assertEquals(null, noMkt)
    }

    @Test
    fun `h2h counts from both venues and skips missing scores`() {        fun row(homeId: Int, awayId: Int, gh: Int?, ga: Int?) =
            com.footpredict.local.data.local.entity.MatchEntity(
                id = gh.hashCode() + ga.hashCode() + homeId, externalIdsJson = null,
                leagueId = 39, season = 2026, round = null, dateTimestamp = 1L,
                statusShort = "FT", statusLong = null,
                homeTeamId = homeId, homeTeamName = "A", homeTeamLogo = null,
                awayTeamId = awayId, awayTeamName = "B", awayTeamLogo = null,
                goalsHome = gh, goalsAway = ga, venueName = null, venueCity = null,
                sourcesJson = null, conflictsJson = null, lastUpdated = 0L
            )
        val rows = listOf(
            row(1, 2, 2, 1),   // A gagne à domicile
            row(2, 1, 0, 3),   // A gagne à l’extérieur
            row(1, 2, 1, 1),   // nul
            row(1, 2, null, null) // ignoré
        )
        val h2h = computeH2H(rows, 1, 2)
        assertEquals(2, h2h.homeWins)
        assertEquals(1, h2h.draws)
        assertEquals(0, h2h.awayWins)
        assertEquals(3, h2h.total)
    }

    @Test
    fun `date window enforces defaults and custom ranges`() {        val nowMs = 1_790_000_000_000L
        val dayMs = 86_400_000L
        // Fenêtres par défaut : 15 j avant / 7 j après... (15 à venir, 7 passés).
        assertTrue(PickHelpers.inDateWindow((nowMs + 10 * dayMs) / 1000, nowMs, 15, 7, null))
        assertTrue(!PickHelpers.inDateWindow((nowMs + 20 * dayMs) / 1000, nowMs, 15, 7, null))
        assertTrue(PickHelpers.inDateWindow((nowMs - 5 * dayMs) / 1000, nowMs, 15, 7, null))
        assertTrue(!PickHelpers.inDateWindow((nowMs - 10 * dayMs) / 1000, nowMs, 15, 7, null))
        // Intervalle personnalisé prioritaire.
        val range = (nowMs + dayMs) to (nowMs + 2 * dayMs)
        assertTrue(PickHelpers.inDateWindow((nowMs + 36 * 3_600_000) / 1000, nowMs, 15, 7, range))
        assertTrue(!PickHelpers.inDateWindow((nowMs + 10 * dayMs) / 1000, nowMs, 15, 7, range))
        // Assainissement des réglages.
        assertEquals(90, FilterWindows.sanitize(999, 7).upcomingDays)
        assertEquals(1, FilterWindows.sanitize(15, -3).pastDays)
    }

    @Test
    fun `summary counts won lost pending and hit rate`() {
        fun settled(id: Int, hit: Boolean) =
            com.footpredict.local.domain.model.SettledPrediction(
                matchId = id, predictedOutcome = "HOME", actualOutcome = "HOME",
                homeWinProb = 0.6, drawProb = 0.25, awayWinProb = 0.15,
                confidence = 0.6, wasHit = hit, edgeOnPredicted = null, settledAt = 1L
            )
        val summary = buildSummary(
            listOf(settled(1, true), settled(2, false), settled(3, true)),
            pending = 5
        )
        assertEquals(3, summary.totalPredicted)
        assertEquals(2, summary.won)
        assertEquals(1, summary.lost)
        assertEquals(5, summary.pending)
        assertEquals(66.67, summary.hitRatePct, 0.01)
        val empty = buildSummary(emptyList(), 0)
        assertEquals(0.0, empty.hitRatePct, 0.0)
    }

    @Test
    fun `settled replays prematch probs for finished cards`() {
        val sp = com.footpredict.local.domain.model.SettledPrediction(
            matchId = 7, predictedOutcome = "HOME", actualOutcome = "AWAY",
            homeWinProb = 0.55, drawProb = 0.27, awayWinProb = 0.18,
            confidence = 0.6, wasHit = false, edgeOnPredicted = null, settledAt = 1L,
            over25Prob = 0.70, bttsYesProb = 0.40
        )
        val card = settledToCardPrediction(sp)
        assertEquals(7, card.matchId)
        assertEquals(0.55, card.homeWinProb, 0.0)
        assertEquals(0.70, card.over25Prob!!, 0.0)
        assertEquals(0.40, card.bttsYesProb!!, 0.0)
        assertEquals(com.footpredict.local.domain.model.DivergenceLevel.MEDIUM, card.divergenceLevel)
        assertTrue(card.valueBets.isEmpty())
    }
}
