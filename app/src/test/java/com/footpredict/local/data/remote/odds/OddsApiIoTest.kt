package com.footpredict.local.data.remote.odds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OddsApiIoTest {

    @Test
    fun `token overlap matches name variants`() {
        val s = OddsApiIoDataSource
        assertTrue(s.tokenOverlap("brighton and hove albion", "brighton hove albion fc") >= 0.5)
        assertTrue(s.tokenOverlap("manchester city", "chelsea") < 0.5)
    }

    @Test
    fun `league mapping covers known leagues only`() {
        val s = OddsApiIoDataSource
        assertEquals("soccer_epl", s.mapLeagueToSportKey(39))
        assertEquals("soccer_usa_mls", s.mapLeagueToSportKey(253))
        assertNull(s.mapLeagueToSportKey(999999))
    }
}
