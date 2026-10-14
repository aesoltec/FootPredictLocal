package com.footpredict.local.data.remote.supabase

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Le schéma d'IDs doit matcher `_shared/ids.ts` (Edge Functions).
 * Vecteurs documentés des deux côtés.
 */
class ScrapedIdsTest {

    @Test
    fun `normalize lowercases and strips accents`() {
        assertEquals("manchester united", ScrapedIds.normalize("Manchester United"))
        assertEquals("paris saint germain", ScrapedIds.normalize("Paris Saint-Germain"))
        assertEquals("real madrid", ScrapedIds.normalize("  Real   Madrid!! "))
        assertEquals("fc bayern munchen", ScrapedIds.normalize("FC Bayern München"))
        assertEquals("borussia m gladbach", ScrapedIds.normalize("Borussia M.Gladbach"))
    }

    @Test
    fun `javaHash matches String hashCode vectors`() {
        assertEquals(96354, ScrapedIds.javaHash("abc"))
        assertEquals(0, ScrapedIds.javaHash(""))
        assertEquals("abc".hashCode(), ScrapedIds.javaHash("abc"))
        assertEquals(
            "manchester united".hashCode(),
            ScrapedIds.javaHash("manchester united")
        )
    }

    @Test
    fun `unifiedTeamId is stable and name-based`() {
        val a = ScrapedIds.unifiedTeamId("Arsenal")
        assertEquals(a, ScrapedIds.unifiedTeamId("  arsenal "))
        assertEquals("arsenal".hashCode(), a)
    }
}
