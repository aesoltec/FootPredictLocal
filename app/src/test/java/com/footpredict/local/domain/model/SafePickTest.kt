package com.footpredict.local.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafePickTest {

    @Test
    fun `over25 downgrades to over15`() {
        val best = bestMarketPick(0.4, 0.3, 0.3, 0.75, 0.5)
        val safe = safeDowngrade(best, 0.4, 0.3, 0.3, 0.92, 0.40)
        assertEquals(SafeKind.OVER15, safe!!.kind)
        assertEquals(0.92, safe.probability, 0.0)
    }

    @Test
    fun `under25 downgrades to under35`() {
        val best = bestMarketPick(0.3, 0.3, 0.2, 0.30, 0.4)
        val safe = safeDowngrade(best, 0.3, 0.3, 0.2, 0.70, 0.10)
        assertEquals(SafeKind.UNDER35, safe!!.kind)
        assertEquals(0.90, safe.probability, 0.001)
    }

    @Test
    fun `btts yes downgrades to over15 goals`() {
        val best = bestMarketPick(0.3, 0.25, 0.25, 0.5, 0.65)
        val safe = safeDowngrade(best, 0.3, 0.25, 0.25, 0.89, 0.40)
        assertEquals(SafeKind.OVER15, safe!!.kind)
    }

    @Test
    fun `home win downgrades to 1x and draw to 12`() {
        val home = bestMarketPick(0.6, 0.25, 0.15, null, null)
        assertEquals(
            SafeKind.DC_1X,
            safeDowngrade(home, 0.6, 0.25, 0.15, null, null)!!.kind
        )
        val draw = bestMarketPick(0.3, 0.4, 0.3, null, null)
        val dc = safeDowngrade(draw, 0.3, 0.4, 0.3, null, null)!!
        assertEquals(SafeKind.DC_12, dc.kind)
        assertEquals(0.60, dc.probability, 0.001)
    }

    @Test
    fun `missing data yields nothing`() {
        val best = bestMarketPick(0.4, 0.3, 0.3, 0.75, 0.5)
        assertNull(safeDowngrade(best, 0.4, 0.3, 0.3, null, null))
    }
}
