package com.footpredict.local.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CornersPredictionTest {

    @Test
    fun `missing xg yields no data`() {
        val est = estimateCorners(1, null, 1.5)
        assertFalse(est.hasEnoughData)
        assertNull(est.direction)
        assertNull(est.expectedCorners)
        assertEquals(0.0, est.confidence, 0.0)
    }

    @Test
    fun `very open match proposes over`() {
        // Dangerosité 4,5 → volume 11,7 → confiance écrêtée 0,85.
        val est = estimateCorners(1, 2.5, 2.0)
        assertTrue(est.hasEnoughData)
        assertEquals(CornersDirection.OVER, est.direction)
        assertTrue(est.confidence >= CORNERS_MIN_CONFIDENCE)
    }

    @Test
    fun `very closed match proposes under`() {
        // Dangerosité 0,9 → volume 5,94 → |Δ| = 3,56 → confiance 0,85.
        val est = estimateCorners(1, 0.5, 0.4)
        assertTrue(est.hasEnoughData)
        assertEquals(CornersDirection.UNDER, est.direction)
        assertTrue(est.confidence >= CORNERS_MIN_CONFIDENCE)
    }

    @Test
    fun `balanced match proposes nothing`() {
        // Dangerosité 2,6 → volume 8,66 → confiance 0,60 < 0,65.
        val est = estimateCorners(1, 1.3, 1.3)
        assertTrue(est.hasEnoughData)
        assertNull(est.direction)
    }

    @Test
    fun `cornersPicks keeps only actionable sorted by confidence`() {
        fun enriched(id: Int, xgH: Double?, xgA: Double?) = EnrichedMatch(
            fixture = Match(
                id = id, leagueId = 39, season = 2026, round = null,
                dateTimestamp = 100L + id, statusShort = "NS", statusLong = null,
                homeTeamId = 1, homeTeamName = "H$id", homeTeamLogo = null,
                awayTeamId = 2, awayTeamName = "A$id", awayTeamLogo = null,
                goalsHome = null, goalsAway = null, xgHome = xgH, xgAway = xgA,
                venueName = null, venueCity = null
            ),
            homeStats = null, awayStats = null, odds = null,
            eloHome = null, eloAway = null, sources = emptyList(), conflicts = emptyList()
        )
        val picks = cornersPicks(
            listOf(
                enriched(1, 2.5, 2.0),
                enriched(2, 1.3, 1.3),
                enriched(3, null, null),
                enriched(4, 0.5, 0.4)
            )
        )
        assertEquals(listOf(4, 1), picks.map { it.matchId })
        assertTrue(picks.zipWithNext().all { (a, b) -> a.confidence >= b.confidence })
    }
}
