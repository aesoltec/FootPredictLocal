package com.footpredict.local.data.remote.live

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseLiveScoresTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `unified payload parses and maps`() {
        val raw = """
        {
          "matches": [
            {
              "match_id": "api:123",
              "league_id": 39,
              "season": 2026,
              "status_short": "1H",
              "status_detail": "1H",
              "date_ts": 1791631800,
              "home": {"id": "api:33", "name": "Manchester United FC", "logo": "https://x/y.svg"},
              "away": {"id": "api:40", "name": "Liverpool FC", "logo": null},
              "goals_home": 1,
              "goals_away": 0,
              "elapsed": 25,
              "source": "api-football"
            }
          ],
          "cached": false,
          "quota_remaining": 39,
          "errors": []
        }
        """.trimIndent()
        val response = json.decodeFromString(LiveScoresResponse.serializer(), raw)
        assertEquals(1, response.matches.size)
        assertEquals(39, response.quotaRemaining)
        val entity = response.matches.first().toEntity(now = 1L)!!
        assertEquals(39, entity.leagueId)
        assertEquals("1H", entity.statusShort)
        assertEquals(1, entity.goalsHome)
        assertEquals(0, entity.goalsAway)
        assertEquals("Manchester United FC", entity.homeTeamName)
        assertTrue(entity.id < 0)
    }

    @Test
    fun `blank names or dates are dropped`() {
        val dto = LiveScoreDto(
            matchId = "x", leagueId = 39, statusShort = "LIVE", dateTs = 0,
            home = LiveTeamDto(name = " ", logo = null),
            away = LiveTeamDto(name = "B", logo = null)
        )
        assertNull(dto.toEntity())
    }

    @Test
    fun `stable ids are deterministic and negative`() {
        val a = stableLiveId("live:api:123")
        assertEquals(a, stableLiveId("live:api:123"))
        assertTrue(a < 0)
    }
}
