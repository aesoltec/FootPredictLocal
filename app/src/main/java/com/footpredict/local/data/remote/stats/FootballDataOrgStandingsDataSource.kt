package com.footpredict.local.data.remote.stats

import com.footpredict.local.BuildConfig
import com.footpredict.local.data.local.entity.TeamStatsEntity
import com.footpredict.local.data.remote.fixture.FdCompetitions
import com.footpredict.local.data.remote.fixture.FdThrottle
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
 * Classements football-data.org (plan gratuit) → stats d’équipe.
 * reliabilityScore = 0.7 (officiel mais totaux saison SANS split domicile /
 * extérieur : on répartit moitié-moitié — approximation documentée).
 *
 * Throttling : si l’API signale ≤ 1 requête/minute restante, on lève pour
 * reporter à la prochaine synchro (le fournisseur bannit les abus).
 */
class FootballDataOrgStandingsDataSource @Inject constructor(
    private val client: HttpClient
) : StatsRemoteDataSource {

    override val sourceName: String = "football-data.org"
    override val reliabilityScore: Double = 0.7

    override suspend fun fetchTeamStats(
        teamId: Int,
        leagueId: Int,
        season: Int
    ): TeamStatsEntity? {
        if (!RemoteConfig.hasFootballDataKey()) return null
        val code = FdCompetitions.codeFor(leagueId) ?: return null
        val response: HttpResponse =
            client.get("https://api.football-data.org/v4/competitions/$code/standings") {
                header("X-Auth-Token", BuildConfig.FOOTBALL_DATA_KEY)
                parameter("season", season)
            }
        checkThrottle(response)
        val dto: FdStandingsResponse = response.body()
        val row = dto.standings.firstOrNull()?.table?.firstOrNull { it.team.id == teamId }
            ?: return null
        return row.toEntity(leagueId, season)
    }

    companion object {
        fun checkThrottle(response: HttpResponse) {
            FdThrottle.check(response.headers["X-Requests-Available-Minute"]?.toIntOrNull())
        }
    }
}

@Serializable
data class FdStandingsResponse(
    val standings: List<FdStandingTable> = emptyList()
)

@Serializable
data class FdStandingTable(
    val table: List<FdStandingRow> = emptyList()
)

@Serializable
data class FdStandingRow(
    val team: FdStandingTeam,
    val playedGames: Int = 0,
    val won: Int = 0,
    val draw: Int = 0,
    val lost: Int = 0,
    @SerialName("goalsFor") val goalsFor: Int = 0,
    @SerialName("goalsAgainst") val goalsAgainst: Int = 0,
    val form: String? = null
)

@Serializable
data class FdStandingTeam(val id: Int, val name: String, val crest: String? = null)

/**
 * Mapping pur (testable) : totaux répartis moitié-moitié, forme "W,D,L…".
 * Approximation documentée : pas de split domicile/extérieur côté FD.
 */
fun FdStandingRow.toEntity(leagueId: Int, season: Int, now: Long = System.currentTimeMillis()): TeamStatsEntity {
    val half = { total: Int -> total / 2 }
    val rest = { total: Int -> total - total / 2 }
    return TeamStatsEntity(
        teamId = team.id,
        teamName = team.name,
        teamLogo = team.crest,
        leagueId = leagueId,
        season = season,
        playedHome = half(playedGames),
        playedAway = rest(playedGames),
        playedTotal = playedGames,
        goalsForHome = half(goalsFor),
        goalsForAway = rest(goalsFor),
        goalsAgainstHome = half(goalsAgainst),
        goalsAgainstAway = rest(goalsAgainst),
        form = form,
        lastUpdated = now
    )
}
