package com.footpredict.local.data.remote.fixture

import com.footpredict.local.BuildConfig
import com.footpredict.local.data.local.entity.MatchEntity
import com.footpredict.local.data.remote.supabase.RemoteConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * Source fixtures — Football-Data.org (reliability 0.75).
 * Compétitions majeures, classements, scores.
 * Auth : header X-Auth-Token.
 */
class FootballDataOrgFixtureDataSource @Inject constructor(
    private val client: HttpClient
) : FixtureRemoteDataSource {

    override val sourceName: String = "football-data.org"
    override val reliabilityScore: Double = 0.75

    /**
     * @param leagueId  code compétition FD (ex. PL=Premier League).
     *                  Mapping API-Football leagueId → FD code à faire via EntityResolver.
     * @param season    année de début (ex. 2025)
     */
    override suspend fun fetchFixtures(leagueId: Int, season: Int): List<MatchEntity> {
        if (!RemoteConfig.hasFootballDataKey()) return emptyList()
        val competitionCode = FdCompetitions.codeFor(leagueId) ?: return emptyList()

        val raw: HttpResponse = client.get("https://api.football-data.org/v4/competitions/$competitionCode/matches") {
            header("X-Auth-Token", BuildConfig.FOOTBALL_DATA_KEY)
            parameter("season", season)
        }
        FdThrottle.check(raw.headers["X-Requests-Available-Minute"]?.toIntOrNull())
        val response: FdMatchesResponse = raw.body()

        val now = System.currentTimeMillis()
        return response.matches.mapNotNull { m ->
            val home = m.homeTeam ?: return@mapNotNull null
            val away = m.awayTeam ?: return@mapNotNull null
            MatchEntity(
                id = m.id,
                externalIdsJson = """{"football-data.org":"${m.id}"}""",
                leagueId = leagueId,
                season = season,
                round = m.matchday?.let { "MD $it" },
                dateTimestamp = parseUtcToEpoch(m.utcDate),
                statusShort = mapStatus(m.status),
                statusLong = m.status,
                homeTeamId = home.id,
                homeTeamName = home.name,
                homeTeamLogo = home.crest,
                awayTeamId = away.id,
                awayTeamName = away.name,
                awayTeamLogo = away.crest,
                goalsHome = m.score?.fullTime?.home,
                goalsAway = m.score?.fullTime?.away,
                venueName = null,
                venueCity = null,
                sourcesJson = sourceName,
                conflictsJson = null,
                lastUpdated = now
            )
        }
    }

    private fun parseUtcToEpoch(utc: String?): Long {
        if (utc.isNullOrBlank()) return 0L
        return try {
            // 2025-10-09T15:00:00Z → epoch seconds approx via Instant
            java.time.Instant.parse(utc).epochSecond
        } catch (_: Exception) {
            0L
        }
    }

    private fun mapStatus(status: String?): String = when (status?.uppercase()) {
        "SCHEDULED", "TIMED" -> "NS"
        "IN_PLAY", "LIVE" -> "LIVE"
        "PAUSED" -> "HT"
        "FINISHED" -> "FT"
        "POSTPONED" -> "PST"
        "CANCELLED", "SUSPENDED" -> "CANC"
        else -> status ?: "NS"
    }
}

@Serializable
data class FdMatchesResponse(val matches: List<FdMatchDto> = emptyList())

@Serializable
data class FdMatchDto(
    val id: Int,
    val utcDate: String? = null,
    val status: String? = null,
    val matchday: Int? = null,
    val homeTeam: FdTeamDto? = null,
    val awayTeam: FdTeamDto? = null,
    val score: FdScoreDto? = null
)

@Serializable
data class FdTeamDto(val id: Int, val name: String, val crest: String? = null)

@Serializable
data class FdScoreDto(
    val fullTime: FdScoreLineDto? = null
)

@Serializable
data class FdScoreLineDto(
    val home: Int? = null,
    val away: Int? = null
)
