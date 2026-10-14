package com.footpredict.local.data.remote.stats

import com.footpredict.local.BuildConfig
import com.footpredict.local.data.local.entity.TeamStatsEntity
import com.footpredict.local.data.remote.dto.ApiResponse
import com.footpredict.local.data.remote.dto.TeamStatisticsResponseDto
import com.footpredict.local.data.remote.supabase.RemoteConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import javax.inject.Inject

/**
 * Stats d’équipe — API-Football v3 (reliability 0.8).
 */
class ApiFootballStatsDataSource @Inject constructor(
    private val client: HttpClient
) : StatsRemoteDataSource {

    override val sourceName: String = "api-football"
    override val reliabilityScore: Double = 0.8

    override suspend fun fetchTeamStats(
        teamId: Int,
        leagueId: Int,
        season: Int
    ): TeamStatsEntity? {
        if (!RemoteConfig.hasApiFootballKey()) return null
        val response: ApiResponse<TeamStatisticsResponseDto> = client.get("teams/statistics") {
            header("x-apisports-key", BuildConfig.API_FOOTBALL_KEY)
            parameter("team", teamId)
            parameter("league", leagueId)
            parameter("season", season)
        }.body()

        if (response.errors.isNotEmpty()) {
            throw Exception("API-Football stats error: ${response.errors.joinToString()}")
        }

        val dto = response.response
        val now = System.currentTimeMillis()
        return TeamStatsEntity(
            teamId = dto.team.id,
            teamName = dto.team.name,
            teamLogo = dto.team.logo,
            leagueId = dto.league.id,
            season = dto.league.season,
            playedHome = dto.fixtures.played.home,
            playedAway = dto.fixtures.played.away,
            playedTotal = dto.fixtures.played.total,
            goalsForHome = dto.goals.goalsFor.total.home,
            goalsForAway = dto.goals.goalsFor.total.away,
            goalsAgainstHome = dto.goals.against.total.home,
            goalsAgainstAway = dto.goals.against.total.away,
            form = dto.form,
            lastUpdated = now
        )
    }
}
