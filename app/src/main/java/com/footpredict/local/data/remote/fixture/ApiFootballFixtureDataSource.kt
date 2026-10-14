package com.footpredict.local.data.remote.fixture

import com.footpredict.local.BuildConfig
import com.footpredict.local.data.local.entity.MatchEntity
import com.footpredict.local.data.remote.dto.ApiResponse
import com.footpredict.local.data.remote.dto.FixtureResponseDto
import com.footpredict.local.data.remote.supabase.RemoteConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import javax.inject.Inject

/**
 * Source fixtures — API-Football v3 (priorité haute, reliability 0.8).
 */
class ApiFootballFixtureDataSource @Inject constructor(
    private val client: HttpClient
) : FixtureRemoteDataSource {

    override val sourceName: String = "api-football"
    override val reliabilityScore: Double = 0.8

    override suspend fun fetchFixtures(leagueId: Int, season: Int): List<MatchEntity> {
        if (!RemoteConfig.hasApiFootballKey()) return emptyList()
        val response: ApiResponse<List<FixtureResponseDto>> = client.get("fixtures") {
            header("x-apisports-key", BuildConfig.API_FOOTBALL_KEY)
            parameter("league", leagueId)
            parameter("season", season)
        }.body()

        if (response.errors.isNotEmpty()) {
            throw Exception("API-Football fixtures error: ${response.errors.joinToString()}")
        }

        val now = System.currentTimeMillis()
        return response.response.map { dto ->
            MatchEntity(
                id = dto.fixture.id,
                externalIdsJson = """{"api-football":"${dto.fixture.id}"}""",
                leagueId = dto.league.id,
                season = dto.league.season,
                round = dto.league.round,
                dateTimestamp = dto.fixture.timestamp,
                statusShort = dto.fixture.status.short,
                statusLong = dto.fixture.status.long,
                homeTeamId = dto.teams.home.id,
                homeTeamName = dto.teams.home.name,
                homeTeamLogo = dto.teams.home.logo,
                awayTeamId = dto.teams.away.id,
                awayTeamName = dto.teams.away.name,
                awayTeamLogo = dto.teams.away.logo,
                goalsHome = dto.goals.home,
                goalsAway = dto.goals.away,
                venueName = dto.fixture.venue?.name,
                venueCity = dto.fixture.venue?.city,
                sourcesJson = sourceName,
                conflictsJson = null,
                lastUpdated = now
            )
        }
    }
}
