package com.footpredict.local.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MarketPickTest {

    @Test
    fun `strongest 1x2 wins without markets`() {
        val best = bestMarketPick(0.5, 0.3, 0.2, null, null)
        assertEquals(MarketKind.HOME_WIN, best.kind)
        assertEquals(0.5, best.probability, 0.0)
    }

    @Test
    fun `over can beat 1x2`() {
        val best = bestMarketPick(0.4, 0.3, 0.3, 0.75, 0.5)
        assertEquals(MarketKind.OVER25, best.kind)
        assertEquals(0.75, best.probability, 0.0)
    }
    @Test
    fun `btts-no can win`() {
        val best = bestMarketPick(0.3, 0.3, 0.4, 0.4, 0.2)
        assertEquals(MarketKind.BTTS_NO, best.kind)
        assertEquals(0.8, best.probability, 0.001)
    }

    @Test
    fun `over below 062 is blocked and falls back to 1x2`() {
        // Recalibrage 2026-10-13 : Over à 60,6 % (cas Liverpool 0-0 Fulham)
        // ne doit plus être proposé ; le 1X2 dominant reprend la main.
        val best = bestMarketPick(0.5, 0.3, 0.2, 0.606, null)
        assertEquals(MarketKind.HOME_WIN, best.kind)
    }

    @Test
    fun `over above 062 still wins`() {
        val best = bestMarketPick(0.4, 0.3, 0.3, 0.63, null)
        assertEquals(MarketKind.OVER25, best.kind)
    }

    @Test
    fun `market hit validates proposed market against final score`() {
        // Audit externe 2026-10-14 : BTTS-No sur 5-3 = PERDU (même si 1X2 bon).
        assertEquals(false, marketHit(MarketKind.BTTS_NO, 5, 3))
        assertEquals(true, marketHit(MarketKind.BTTS_YES, 2, 1))
        assertEquals(false, marketHit(MarketKind.BTTS_YES, 3, 0))
        assertEquals(true, marketHit(MarketKind.BTTS_NO, 0, 3))
        assertEquals(true, marketHit(MarketKind.OVER25, 2, 2))
        assertEquals(false, marketHit(MarketKind.OVER25, 1, 1))
        assertEquals(true, marketHit(MarketKind.UNDER25, 1, 0))
        assertEquals(true, marketHit(MarketKind.HOME_WIN, 2, 1))
        assertEquals(true, marketHit(MarketKind.DRAW, 1, 1))
        assertEquals(true, marketHit(MarketKind.AWAY_WIN, 0, 3))
        assertEquals(true, marketHit(MarketKind.DC_1X, 1, 1))
        assertEquals(true, marketHit(MarketKind.DC_12, 2, 1))
        assertEquals(false, marketHit(MarketKind.DC_12, 1, 1))
        assertEquals(true, marketHit(MarketKind.DC_X2, 0, 1))
    }

    @Test
    fun `invalid market values are ignored`() {
        val best = bestMarketPick(0.5, 0.3, 0.2, 1.5, -0.2)
        assertEquals(MarketKind.HOME_WIN, best.kind)
    }

    @Test
    fun `draw breaks ties by priority`() {
        val best = bestMarketPick(0.4, 0.4, 0.2, null, null)
        assertEquals(MarketKind.HOME_WIN, best.kind)
    }
}
