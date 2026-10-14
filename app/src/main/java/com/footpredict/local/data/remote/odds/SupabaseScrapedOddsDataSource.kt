package com.footpredict.local.data.remote.odds

import com.footpredict.local.BuildConfig
import com.footpredict.local.data.local.entity.OddsEntity
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
 * Cotes scrapées (OddsPortal / OddsMath) via Supabase.
 * reliabilityScore = 0.7 (fallback derrière Odds-API.io).
 */
class SupabaseScrapedOddsDataSource @Inject constructor(
    private val client: HttpClient
) : OddsRemoteDataSource {

    override val sourceName: String = "oddsportal-supabase"
    override val reliabilityScore: Double = 0.7

    override suspend fun fetchOdds(matchId: Int): OddsEntity? {
        if (!RemoteConfig.isSupabaseConfigured()) return null
        val rows: List<ScrapedOddsRowDto> = client.get("${BuildConfig.SUPABASE_URL}/rest/v1/scraped_odds") {
            header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            parameter("match_id", "eq.$matchId")
            parameter("select", "*")
            parameter("order", "last_updated.desc")
            parameter("limit", "1")
        }.body()

        val row = rows.firstOrNull() ?: return null
        return row.toEntity(sourceName)
    }

    override suspend fun fetchAllOdds(): Map<Int, OddsEntity> {
        if (!RemoteConfig.isSupabaseConfigured()) return emptyMap()
        val rows: List<ScrapedOddsRowDto> = try {
            client.get("${BuildConfig.SUPABASE_URL}/rest/v1/scraped_odds") {
                header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
                parameter("select", "*")
                parameter("order", "last_updated.desc")
                parameter("limit", "1000")
            }.body()
        } catch (_: Exception) {
            return emptyMap()
        }
        // 1 ligne par match : la plus récente (tri serveur décroissant).
        val byMatch = mutableMapOf<Int, OddsEntity>()
        for (row in rows) {
            byMatch.putIfAbsent(row.matchId, row.toEntity(sourceName))
        }
        return byMatch
    }
}

@Serializable
data class ScrapedOddsRowDto(
    @SerialName("match_id") val matchId: Int,
    val bookmaker: String,
    @SerialName("home_odds") val homeOdds: Double? = null,
    @SerialName("draw_odds") val drawOdds: Double? = null,
    @SerialName("away_odds") val awayOdds: Double? = null,
    @SerialName("over_25") val over25: Double? = null,
    @SerialName("under_25") val under25: Double? = null,
    @SerialName("last_updated") val lastUpdated: Long
)

/** Mapping pur (testable sans réseau). */
fun ScrapedOddsRowDto.toEntity(source: String): OddsEntity = OddsEntity(
    matchId = matchId,
    bookmaker = bookmaker,
    homeOdds = homeOdds,
    drawOdds = drawOdds,
    awayOdds = awayOdds,
    over25Odds = over25,
    under25Odds = under25,
    source = source,
    lastUpdated = lastUpdated
)
