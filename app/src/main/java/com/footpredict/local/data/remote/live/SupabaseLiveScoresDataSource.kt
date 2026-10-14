package com.footpredict.local.data.remote.live

import com.footpredict.local.BuildConfig
import com.footpredict.local.data.local.entity.MatchEntity
import com.footpredict.local.data.remote.supabase.RemoteConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import kotlin.math.abs

/**
 * Source live mutualisée — Edge Function `get-live-scores` (scores en direct,
 * ~40 ligues, modèle JSON unifié). La clé API-Football reste CÔTÉ SERVEUR
 * (jamais dans l’app) ; le téléphone consomme le cache partagé 90 s
 * (batterie + quota préservés : 1 appel serveur pour tous).
 *
 * Statuts déjà canoniques côté fonction (NS/LIVE/1H/HT/2H/ET/FT/PST/CANC) :
 * retransmis tels quels (le validateur live ne connaît que ces codes).
 * IDs stables négatifs ([stableLiveId]) : jamais de collision avec les vrais
 * IDs fournisseurs ; la fusion inter-sources se fait sur noms+date.
 */
class SupabaseLiveScoresDataSource @Inject constructor(
    private val client: HttpClient
) {

    suspend fun fetchLiveScores(): List<MatchEntity> {
        if (!RemoteConfig.isSupabaseConfigured()) return emptyList()
        val response: LiveScoresResponse = client
            .get("${BuildConfig.SUPABASE_URL}/functions/v1/get-live-scores") {
                header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            }.body()
        val now = System.currentTimeMillis()
        return response.matches.mapNotNull { it.toEntity(now) }
    }
}

/** Hash stable Java-String (même algorithme que le backend, testé). */
fun stableLiveId(key: String): Int {
    var h = 0
    for (c in key) h = 31 * h + c.code
    return -(abs(h) % 200_000_000) - 100_000_000
}

@Serializable
data class LiveScoresResponse(
    val matches: List<LiveScoreDto> = emptyList(),
    val cached: Boolean = false,
    @SerialName("quota_remaining") val quotaRemaining: Int? = null,
    val errors: List<String> = emptyList()
)

@Serializable
data class LiveTeamDto(
    val id: String? = null,
    val name: String,
    val logo: String? = null
)

@Serializable
data class LiveScoreDto(
    @SerialName("match_id") val matchId: String,
    @SerialName("league_id") val leagueId: Int,
    val season: Int? = null,
    @SerialName("status_short") val statusShort: String,
    @SerialName("status_detail") val statusDetail: String? = null,
    @SerialName("date_ts") val dateTs: Long,
    val home: LiveTeamDto,
    val away: LiveTeamDto,
    @SerialName("goals_home") val goalsHome: Int? = null,
    @SerialName("goals_away") val goalsAway: Int? = null,
    val elapsed: Int? = null,
    val source: String = ""
) {
    private fun seasonOf(ts: Long): Int {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = ts * 1000
        val month = cal.get(java.util.Calendar.MONTH) + 1
        val year = cal.get(java.util.Calendar.YEAR)
        return if (month >= 8) year else year - 1
    }

    /** Mapping pur (testable sans réseau). */
    fun toEntity(now: Long = System.currentTimeMillis()): MatchEntity? {
        if (home.name.isBlank() || away.name.isBlank()) return null
        if (dateTs <= 0) return null
        return MatchEntity(
            id = stableLiveId("live:$matchId"),
            externalIdsJson = """{"live":"$matchId"}""",
            leagueId = leagueId,
            season = season ?: seasonOf(dateTs),
            round = null,
            dateTimestamp = dateTs,
            statusShort = statusShort,
            statusLong = statusDetail,
            homeTeamId = stableLiveId("team:${home.id ?: home.name}"),
            homeTeamName = home.name,
            homeTeamLogo = home.logo,
            awayTeamId = stableLiveId("team:${away.id ?: away.name}"),
            awayTeamName = away.name,
            awayTeamLogo = away.logo,
            goalsHome = goalsHome,
            goalsAway = goalsAway,
            xgHome = null,
            xgAway = null,
            venueName = null,
            venueCity = null,
            sourcesJson = source.ifBlank { "live" },
            conflictsJson = null,
            lastUpdated = now
        )
    }
}
