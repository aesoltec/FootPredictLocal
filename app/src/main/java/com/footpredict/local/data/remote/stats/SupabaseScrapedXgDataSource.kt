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
 * xG scrapé via Supabase Edge Function (Understat).
 * reliabilityScore = 0.9 (priorité haute pour xG).
 * L’app consomme uniquement la table REST exposée par Supabase.
 */
class SupabaseScrapedXgDataSource @Inject constructor(
    private val client: HttpClient
) : StatsRemoteDataSource {

    override val sourceName: String = "understat-supabase"
    override val reliabilityScore: Double = 0.9

    override suspend fun fetchTeamStats(
        teamId: Int,
        leagueId: Int,
        season: Int
    ): TeamStatsEntity? {
        if (!RemoteConfig.isSupabaseConfigured()) return null
        // Appel REST Supabase : table "xg_stats"
        val rows: List<XgRowDto> = client.get("${BuildConfig.SUPABASE_URL}/rest/v1/xg_stats") {
            header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            parameter("unified_team_id", "eq.$teamId")
            parameter("league_id", "eq.$leagueId")
            parameter("season", "eq.$season")
            parameter("select", "*")
        }.body()

        val row = rows.firstOrNull() ?: return null
        val now = System.currentTimeMillis()

        return TeamStatsEntity(
            teamId = row.unifiedTeamId,
            teamName = row.teamName,
            teamLogo = null,
            leagueId = row.leagueId,
            season = row.season,
            xgForHome = row.xgForHome,
            xgForAway = row.xgForAway,
            xgaHome = row.xgaHome,
            xgaAway = row.xgaAway,
            npxgFor = row.npxgFor,
            lastUpdated = now
        )
    }

    override suspend fun fetchLeagueStats(leagueId: Int, season: Int): List<TeamStatsEntity> {
        if (!RemoteConfig.isSupabaseConfigured()) return emptyList()
        val now = System.currentTimeMillis()
        val rows: List<XgRowDto> = client.get("${BuildConfig.SUPABASE_URL}/rest/v1/xg_stats") {
            header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            parameter("league_id", "eq.$leagueId")
            parameter("season", "eq.$season")
            parameter("select", "*")
        }.body()

        return rows.map { row ->
            TeamStatsEntity(
                teamId = row.unifiedTeamId,
                teamName = row.teamName,
                teamLogo = null,
                leagueId = row.leagueId,
                season = row.season,
                xgForHome = row.xgForHome,
                xgForAway = row.xgForAway,
                xgaHome = row.xgaHome,
                xgaAway = row.xgaAway,
                npxgFor = row.npxgFor,
                lastUpdated = now
            )
        }
    }
}

@Serializable
data class XgRowDto(
    @SerialName("unified_team_id") val unifiedTeamId: Int,
    @SerialName("team_name") val teamName: String,
    @SerialName("league_id") val leagueId: Int,
    val season: Int,
    @SerialName("xg_for_home") val xgForHome: Double? = null,
    @SerialName("xg_for_away") val xgForAway: Double? = null,
    @SerialName("xga_home") val xgaHome: Double? = null,
    @SerialName("xga_away") val xgaAway: Double? = null,
    @SerialName("npxg_for") val npxgFor: Double? = null
)
