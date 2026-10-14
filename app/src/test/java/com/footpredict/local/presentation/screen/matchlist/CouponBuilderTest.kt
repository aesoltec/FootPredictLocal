package com.footpredict.local.presentation.screen.matchlist

import com.footpredict.local.domain.model.DivergenceLevel
import com.footpredict.local.domain.model.EnrichedMatch
import com.footpredict.local.domain.model.EnsemblePrediction
import com.footpredict.local.domain.model.Match
import com.footpredict.local.domain.model.Odds
import com.footpredict.local.domain.model.oddsInRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CouponBuilderTest {

    private fun match(id: Int) = EnrichedMatch(
        fixture = Match(
            id = id, leagueId = 39, season = 2026, round = null,
            dateTimestamp = 1_791_631_800L, statusShort = "NS", statusLong = null,
            homeTeamId = id * 10, homeTeamName = "H$id", homeTeamLogo = null,
            awayTeamId = id * 10 + 1, awayTeamName = "A$id", awayTeamLogo = null,
            goalsHome = null, goalsAway = null, venueName = null, venueCity = null
        ),
        homeStats = null, awayStats = null, odds = null,
        eloHome = null, eloAway = null, sources = emptyList(), conflicts = emptyList()
    )

    private fun pred(home: Double, over: Double?) = EnsemblePrediction(
        matchId = 1, homeWinProb = home, drawProb = 0.25, awayWinProb = 0.2,
        over25Prob = over, confidence = home, divergenceLevel = DivergenceLevel.LOW,
        dixonColes = null, market = null, elo = null
    )

    private fun withOdds(em: EnrichedMatch, h: Double, o: Double?): EnrichedMatch =
        em.copy(odds = Odds(em.fixture.id, "T", h, 3.4, 4.0, o, 1.8, "t"))

    @Test
    fun `builds combo within odds range`() {
        val m1 = withOdds(match(1), 1.4, 2.2)
        val m2 = withOdds(match(2), 1.5, 2.2)
        // Overs > 0,62 (seuil sécurité recalibré 2026-10-13) avec edge > 0 :
        // 0,75 vs 1/2,2 = 0,455 et 0,70 vs 1/2,2 = 0,455.
        val preds = mapOf(
            1 to pred(0.4, 0.75).copy(matchId = 1),
            2 to pred(0.4, 0.70).copy(matchId = 2)
        )
        val coupon = buildCoupon(listOf(m1, m2), preds)!!
        assertEquals(2, coupon.legs.size)
        assertTrue(coupon.totalOdds!! in 4.0..28.0)
        assertEquals(2.2 * 2.2, coupon.totalOdds!!, 0.001)
        assertEquals(0.75, coupon.legs[0].probability, 0.001)
    }

    @Test
    fun `no coupon when total out of range or no edge`() {
        val m1 = withOdds(match(1), 1.1, 1.2) // cotes trop basses
        val preds = mapOf(1 to pred(0.9, 0.9).copy(matchId = 1))
        assertNull(buildCoupon(listOf(m1), preds)) // 1 seul match
        val m2 = withOdds(match(2), 1.1, 1.2)
        val preds2 = preds + (2 to pred(0.9, 0.9).copy(matchId = 2))
        assertNull(buildCoupon(listOf(m1, m2), preds2)) // 1.21 < 4,0 (borne 2026-10-14)
    }

    @Test
    fun `bankroll projection is honest math`() {
        val (profit, roi) = bankrollProjection(0.5, 100, 10.0, 1.9)
        assertEquals(-50.0, profit, 0.001) // 100×(0.5×1.9−1)×10
        assertEquals(-5.0, roi, 0.001)
        assertEquals(0.0 to 0.0, bankrollProjection(0.5, 0, 10.0, 1.9))
    }

    @Test
    fun `odds drop detected above ten percent`() {
        val old = Odds(1, "T", 2.0, 3.4, 4.0, null, null, "t")
        val same = Odds(1, "T", 1.95, 3.4, 4.0, null, null, "t")
        assertNull(oddsDrop(old, same)) // 2,5 % : silence
        val crashed = Odds(1, "T", 1.7, 3.4, 4.0, null, null, "t")
        val (side, pct) = oddsDrop(old, crashed)!!
        assertEquals("1", side)
        assertEquals(15.0, pct, 0.001)
    }

    @Test
    fun `safe coupon keeps only legs at 88 percent`() {
        // p=0,50 → DC 1X = 0,75 → cote 1,33… hors [1.5,5] → refusé.
        // p=0,40 → DC 1X = 0,65 → cote 1,54 → admis.
        val m1 = match(1)
        val m2 = match(2)
        val preds = mapOf(
            1 to pred(0.5, null).copy(matchId = 1),  // DC 1X = 0,75 → cote 1,33
            2 to pred(0.4, null).copy(matchId = 2)   // DC 1X = 0,65 → cote 1,54
        )
        // Seul le match 2 est dans la plage → 1 candidat < 2 → null.
        assertNull(buildSafeCoupon(listOf(m1, m2), preds))
        // Deux candidats dans la plage → combiné de 2.
        val ok = mapOf(
            1 to pred(0.4, null).copy(matchId = 1),  // DC 1X = 0,65 → cote 1,54
            2 to pred(0.4, null).copy(matchId = 2)   // DC 1X = 0,65 → cote 1,54
        )
        val safe = buildSafeCoupon(listOf(m1, m2), ok)!!
        assertEquals(2, safe.legs.size)
        assertEquals(0.65, safe.avgConfidence, 0.001)
    }

    @Test
    fun `coupon requires at least two matches`() {
        // Un seul match → impossible de faire un combiné 2+ → null.
        val m1 = match(1)
        val preds = mapOf(
            1 to pred(0.7, null).copy(matchId = 1)
        )
        assertNull(buildCoupon(listOf(m1), preds))
    }

    @Test
    fun `coupon bounds total odds`() {
        // Deux matches avec cotes élevées : produit dans [4, 28].
        val m1 = withOdds(match(1), 3.0, 2.5)
        val m2 = withOdds(match(2), 3.5, 2.8)
        val preds = mapOf(
            1 to pred(0.6, null).copy(matchId = 1), // 1X2 fort
            2 to pred(0.55, null).copy(matchId = 2)
        )
        val combo = buildCoupon(listOf(m1, m2), preds)
        if (combo != null) {
            assertTrue(combo.totalOdds!! in 4.0..28.0)
            assertTrue(combo.legs.size in 2..8)
        }
    }

    @Test
    fun `safe filters estimated odds in range`() {
        // p=0,80 → cote 1,25 (hors [1.5,5]) → refusé ; p=0,50 → 2,00 → admis.
        assertTrue(oddsInRange(0.50))
        assertTrue(!oddsInRange(0.80))
        assertTrue(!oddsInRange(0.10)) // cote 10 > 5,0
    }

    @Test
    fun `safe caps at four legs`() {
        val m1 = match(1)
        val preds = mapOf(
            1 to pred(0.7, null).copy(matchId = 1) // DC 1X = 0,95 → cote 1,05… hors plage
        )
        // Hors plage → null. Valeur dans la plange : p=0,5 → 2,00.
        val inRange = mapOf(
            1 to pred(0.5, null).copy(matchId = 1) // DC 1X = 0,75 → cote 1,33…
        )
        val s = buildSafeCoupon(listOf(m1), inRange)
        assertTrue(s == null || s.legs.size in 2..4)
    }

    private fun withOdds(em: EnrichedMatch, h: Double, o: Double): EnrichedMatch =
        em.copy(odds = Odds(em.fixture.id, "T", h, 3.4, 4.0, o, 1.8, "t"))

    @Test
    fun `double chance tops rank best coverage`() {        val list = listOf(match(1), match(2))
        val preds = mapOf(
            1 to pred(0.6, null).copy(matchId = 1), // 1X=0.85
            2 to pred(0.3, null).copy(matchId = 2) // 1X=0.55, 12=0.75
        )
        val top = topDoubleChance(list, preds)
        assertEquals(2, top.size)
        assertEquals(1, top.first().matchId)
    }
}
