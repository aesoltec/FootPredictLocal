package com.footpredict.local.data.remote.fixture

import com.footpredict.local.BuildConfig
import com.footpredict.local.data.local.entity.MatchEntity
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
 * Fixtures scrapées (Understat via Supabase) — chemin zéro-clé API.
 * reliabilityScore = 0.6 (derrière API-Football 0.8 et Football-Data 0.75).
 */
class SupabaseScrapedFixtureDataSource @Inject constructor(
    private val client: HttpClient
) : FixtureRemoteDataSource {

    override val sourceName: String = "understat-supabase"
    override val reliabilityScore: Double = 0.6

    override suspend fun fetchFixtures(leagueId: Int, season: Int): List<MatchEntity> {
        if (!RemoteConfig.isSupabaseConfigured()) return emptyList()
        val rows: List<ScrapedFixtureRowDto> =
            client.get("${BuildConfig.SUPABASE_URL}/rest/v1/scraped_fixtures") {
                header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
                parameter("league_id", "eq.$leagueId")
                parameter("season", "eq.$season")
                parameter("select", "*")
                parameter("order", "date_ts.asc")
            }.body()
        val now = System.currentTimeMillis()
        return rows.map { it.toEntity(now) }
    }
}

@Serializable
data class ScrapedFixtureRowDto(
    @SerialName("match_id") val matchId: Int,
    @SerialName("league_id") val leagueId: Int,
    val season: Int,
    val round: String? = null,
    @SerialName("date_ts") val dateTs: Long,
    @SerialName("status_short") val statusShort: String,
    @SerialName("home_team_id") val homeTeamId: Int,
    @SerialName("home_team_name") val homeTeamName: String,
    @SerialName("away_team_id") val awayTeamId: Int,
    @SerialName("away_team_name") val awayTeamName: String,
    @SerialName("goals_home") val goalsHome: Int? = null,
    @SerialName("goals_away") val goalsAway: Int? = null,
    @SerialName("xg_home") val xgHome: Double? = null,
    @SerialName("xg_away") val xgAway: Double? = null,
    val source: String = "understat",
    @SerialName("last_updated") val lastUpdated: Long = 0
)

/** Mapping pur (testable sans réseau). */
fun ScrapedFixtureRowDto.toEntity(now: Long = System.currentTimeMillis()): MatchEntity =
    MatchEntity(
        id = matchId,
        externalIdsJson = """{"scraped":"$matchId"}""",
        leagueId = leagueId,
        season = season,
        round = round,
        dateTimestamp = dateTs,
        statusShort = statusShort,
        statusLong = statusShort,
        homeTeamId = homeTeamId,
        homeTeamName = homeTeamName,
        homeTeamLogo = null,
        awayTeamId = awayTeamId,
        awayTeamName = awayTeamName,
        awayTeamLogo = null,
        goalsHome = goalsHome,
        goalsAway = goalsAway,
        xgHome = xgHome,
        xgAway = xgAway,
        venueName = null,
        venueCity = null,
        sourcesJson = source,
        conflictsJson = null,
        lastUpdated = now
    )
