package com.footpredict.local.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val get: String? = null,
    val errors: List<String> = emptyList(),
    val results: Int = 0,
    val response: T
)

@Serializable
data class FixtureResponseDto(
    val fixture: FixtureInfoDto,
    val league: LeagueInfoDto,
    val teams: TeamsDto,
    val goals: GoalsDto
)

@Serializable
data class FixtureInfoDto(
    val id: Int,
    val date: String,
    val timestamp: Long,
    val venue: VenueDto? = null,
    val status: StatusDto
)

@Serializable
data class VenueDto(val name: String? = null, val city: String? = null)

@Serializable
data class StatusDto(val long: String? = null, val short: String)

@Serializable
data class LeagueInfoDto(
    val id: Int,
    val name: String,
    val country: String? = null,
    val logo: String? = null,
    val season: Int,
    val round: String? = null
)

@Serializable
data class TeamsDto(val home: TeamDto, val away: TeamDto)

@Serializable
data class TeamDto(
    val id: Int,
    val name: String,
    val logo: String? = null
)

@Serializable
data class GoalsDto(val home: Int? = null, val away: Int? = null)

@Serializable
data class TeamStatisticsResponseDto(
    val league: LeagueInfoDto,
    val team: TeamDto,
    val form: String? = null,
    val fixtures: FixturesStatsDto,
    val goals: GoalsStatsDto
)

@Serializable
data class FixturesStatsDto(
    val played: HomeAwayTotalDto,
    val wins: HomeAwayTotalDto,
    val draws: HomeAwayTotalDto,
    val loses: HomeAwayTotalDto
)

@Serializable
data class HomeAwayTotalDto(val home: Int = 0, val away: Int = 0, val total: Int = 0)

@Serializable
data class GoalsStatsDto(
    @SerialName("for") val goalsFor: GoalsDetailDto,
    val against: GoalsDetailDto
)

@Serializable
data class GoalsDetailDto(
    val total: HomeAwayTotalDto,
    val average: HomeAwayAverageDto? = null
)

@Serializable
data class HomeAwayAverageDto(
    val home: String? = null,
    val away: String? = null,
    val total: String? = null
)
