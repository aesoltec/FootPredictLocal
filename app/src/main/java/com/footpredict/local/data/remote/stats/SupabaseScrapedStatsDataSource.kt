package com.footpredict.local.data.remote.stats

import com.footpredict.local.BuildConfig
import com.footpredict.local.data.local.entity.TeamStatsEntity
import com.footpredict.local.data.remote.supabase.RemoteConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * Agrégats saison scrapés (Understat via Supabase) — buts/matchs domicile et
 * extérieur calculés par l'Edge Function. Permet à Dixon-Coles de tourner
 * sans API-Football. reliabilityScore = 0.65.
 */
class SupabaseScrapedStatsDataSource @Inject constructor(
    private val client: HttpClient
) : StatsRemoteDataSource {

    override val sourceName: String = "understat-stats-supabase"
    override val reliabilityScore: Double = 0.65

    override suspend fun fetchTeamStats(
        teamId: Int,
        leagueId: Int,
        season: Int
    ): TeamStatsEntity? {
        if (!RemoteConfig.isSupabaseConfigured()) return null
        val rows: List<ScrapedTeamStatsRowDto> =
            client.get("${BuildConfig.SUPABASE_URL}/rest/v1/team_season_stats") {
                header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
                parameter("unified_team_id", "eq.$teamId")
                parameter("league_id", "eq.$leagueId")
                parameter("season", "eq.$season")
                parameter("select", "*")
                parameter("limit", "1")
            }.body()
        val row = rows.firstOrNull() ?: return null
        return row.toEntity()
    }

    override suspend fun fetchLeagueStats(leagueId: Int, season: Int): List<TeamStatsEntity> {
        if (!RemoteConfig.isSupabaseConfigured()) return emptyList()
        val now = System.currentTimeMillis()
        val rows: List<ScrapedTeamStatsRowDto> =
            client.get("${BuildConfig.SUPABASE_URL}/rest/v1/team_season_stats") {
                header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
                parameter("league_id", "eq.$leagueId")
                parameter("season", "eq.$season")
                parameter("select", "*")
            }.body()
        return rows.map { it.toEntity(now) }
    }
}

@Serializable
data class ScrapedTeamStatsRowDto(
    @SerialName("unified_team_id") val unifiedTeamId: Int,
    @SerialName("team_name") val teamName: String,
    @SerialName("league_id") val leagueId: Int,
    val season: Int,
    @SerialName("played_home") val playedHome: Int = 0,
    @SerialName("played_away") val playedAway: Int = 0,
    @SerialName("gf_home") val gfHome: Int = 0,
    @SerialName("gf_away") val gfAway: Int = 0,
    @SerialName("ga_home") val gaHome: Int = 0,
    @SerialName("ga_away") val gaAway: Int = 0,
    @SerialName("last_updated") val lastUpdated: Long = 0
)

/** Mapping pur (testable sans réseau). */
fun ScrapedTeamStatsRowDto.toEntity(now: Long = System.currentTimeMillis()): TeamStatsEntity =
    TeamStatsEntity(
        teamId = unifiedTeamId,
        teamName = teamName,
        teamLogo = null,
        leagueId = leagueId,
        season = season,
        playedHome = playedHome,
        playedAway = playedAway,
        playedTotal = playedHome + playedAway,
        goalsForHome = gfHome,
        goalsForAway = gfAway,
        goalsAgainstHome = gaHome,
        goalsAgainstAway = gaAway,
        form = null,
        lastUpdated = now
    )
