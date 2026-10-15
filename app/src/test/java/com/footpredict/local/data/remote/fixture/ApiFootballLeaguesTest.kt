package com.footpredict.local.data.remote.fixture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiFootballLeaguesTest {

    @Test
    fun `ids are unique and names present`() {
        val ids = ApiFootballLeagues.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (league in ApiFootballLeagues.ALL) {
            assertTrue(league.nameFr.isNotBlank())
        }
    }

    @Test
    fun `auto leagues are a subset and cover the historic six`() {
        assertTrue(ApiFootballLeagues.AUTO_IDS.containsAll(listOf(39, 140, 135, 78, 61, 2)))
        val all = ApiFootballLeagues.ALL.map { it.id }.toSet()
        assertTrue(all.containsAll(ApiFootballLeagues.AUTO_IDS))
    }

    @Test
    fun `spot check known competitions`() {
        assertEquals("Premier League (Angleterre)", ApiFootballLeagues.nameFr(39))
        assertEquals("Ligue des Champions (Europe)", ApiFootballLeagues.nameFr(2))
        assertEquals("Eredivisie (Pays-Bas)", ApiFootballLeagues.nameFr(88))
        assertEquals("Liga Portugal (Portugal)", ApiFootballLeagues.nameFr(94))
        assertEquals("Ligue Europa (UEFA)", ApiFootballLeagues.nameFr(3))
        assertEquals("Copa Libertadores (Amérique du Sud)", ApiFootballLeagues.nameFr(13))
        assertEquals("Coupe du monde (FIFA)", ApiFootballLeagues.nameFr(1))
        assertEquals(null, ApiFootballLeagues.nameFr(999999))
    }

    @Test
    fun `requested coverage is present`() {
        val ids = ApiFootballLeagues.ALL.map { it.id }.toSet()
        for (id in listOf(3, 848, 13, 1, 4, 5, 36, 307, 253, 71, 94, 88, 144, 203, 235, 218, 128, 197, 119, 233, 103, 106, 345, 207, 113, 179, 292, 98, 172, 357, 383, 333, 169, 318, 210, 271, 283, 286, 188)) {
            assertTrue("couverture manquante : $id", ids.contains(id))
        }
    }
}
