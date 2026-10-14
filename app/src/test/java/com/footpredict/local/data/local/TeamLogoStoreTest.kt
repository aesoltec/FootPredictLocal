package com.footpredict.local.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TeamLogoStoreTest {

    @Test
    fun `file base name derives from club name`() {
        assertEquals("arsenalfc", TeamLogoStore.fileBaseName("Arsenal FC"))
        assertEquals("brightonhovealbionfc", TeamLogoStore.fileBaseName("Brighton & Hove Albion FC"))
        assertEquals("club", TeamLogoStore.fileBaseName("  !!!  "))
    }

    @Test
    fun `extension follows url with svg default`() {
        assertEquals("svg", TeamLogoStore.extensionOf("https://cdn.example/crests/1.svg"))
        assertEquals("png", TeamLogoStore.extensionOf("https://cdn.example/a.png?ver=2"))
        assertEquals("svg", TeamLogoStore.extensionOf("https://cdn.example/logo"))
    }

    @Test
    fun `logo file combines dir club name and extension`() {
        val dir = File("/data/team_logos")
        assertEquals(
            File("/data/team_logos/manchestercityfc.svg"),
            TeamLogoStore.logoFileFor(dir, "Manchester City FC", "https://cdn.example/x.svg")
        )
    }

    @Test
    fun `same club maps to one file across urls`() {
        val dir = File("/data/team_logos")
        val a = TeamLogoStore.logoFileFor(dir, "Chelsea FC", "https://a.example/c.svg")
        val b = TeamLogoStore.logoFileFor(dir, "Chelsea FC", "https://b.example/crest.svg")
        assertEquals(a, b)
        assertTrue(a.name.startsWith("chelseafc"))
    }
}
