package com.footpredict.local.data.remote.odds

import com.footpredict.local.BuildConfig
import com.footpredict.local.data.local.LocalDataSource
import com.footpredict.local.data.local.entity.OddsEntity
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * Cotes — The Odds API (reliability 0.85).
 *
 * The Odds API utilise des event IDs propres (ex. "e7208…"), incompatibles
 * avec les IDs API-Football. La résolution se fait donc par noms d'équipes
 * normalisés, adossée au match Room (via [LocalDataSource.getMatchById]).
 *
 * Dégradation gracieuse : clé manquante, match inconnu ou réseau KO
 * → retourne null, l'agrégateur bascule sur le scraping Supabase / cache.
 */
class OddsApiIoDataSource @Inject constructor(
    private val client: HttpClient,
    private val local: LocalDataSource
) : OddsRemoteDataSource {

    override val sourceName: String = "odds-api.io"
    override val reliabilityScore: Double = 0.85

    companion object {
        /** 1 crédit par ligue : cache 6 h obligatoire (500 crédits/mois). */
        private const val LEAGUE_CACHE_TTL_MS = 6L * 60L * 60L * 1000L
    }

    private val eventsCache = mutableMapOf<String, Pair<Long, List<OddsEventDto>>>()

    override suspend fun fetchOdds(matchId: Int): OddsEntity? {
        if (BuildConfig.ODDS_API_KEY.isBlank() ||
            BuildConfig.ODDS_API_KEY.startsWith("YOUR_")
        ) return null
        val match = try {
            local.getMatchById(matchId)
        } catch (_: Exception) {
            null
        } ?: return null
        val sportKey = mapLeagueToSportKey(match.leagueId)
        val events = try {
            leagueEventsCached(sportKey)
        } catch (_: Exception) {
            return null
        }
        val found = findEvent(
            events,
            match.homeTeamName,
            match.awayTeamName
        ) ?: return null
        return eventToEntity(found, matchId)
    }

    /** 1 appel réseau par ligue et par 6 h ; repli sur le cache périmé si le réseau échoue. */
    @Synchronized
    private fun cachedEvents(sportKey: String): List<OddsEventDto>? {
        val (at, events) = eventsCache[sportKey] ?: return null
        return if (System.currentTimeMillis() - at < LEAGUE_CACHE_TTL_MS) events else null
    }

    private suspend fun leagueEventsCached(sportKey: String): List<OddsEventDto> {
        cachedEvents(sportKey)?.let { return it }
        return try {
            val fresh = fetchLeagueEvents(sportKey)
            synchronized(this) { eventsCache[sportKey] = System.currentTimeMillis() to fresh }
            fresh
        } catch (e: Exception) {
            synchronized(this) { eventsCache[sportKey]?.second } ?: throw e
        }
    }

    /**
     * Récupère les cotes d’une compétition (batch).
     * À appeler périodiquement et stocker en Room.
     */
    suspend fun fetchLeagueOdds(sportKey: String = "soccer_epl"): List<OddsEntity> {
        val events = fetchLeagueEvents(sportKey)
        val now = System.currentTimeMillis()
        return events.mapNotNull { event ->
            eventToEntity(event, (event.homeTeam + event.awayTeam).hashCode(), now)
        }
    }

    private suspend fun fetchLeagueEvents(sportKey: String): List<OddsEventDto> =
        client.get("https://api.the-odds-api.com/v4/sports/$sportKey/odds") {
            parameter("apiKey", BuildConfig.ODDS_API_KEY)
            parameter("regions", "eu")
            parameter("markets", "h2h,totals")
            parameter("oddsFormat", "decimal")
        }.body()

    private fun eventToEntity(
        event: OddsEventDto,
        matchId: Int,
        now: Long = System.currentTimeMillis()
    ): OddsEntity? {
        val bookmaker = event.bookmakers.firstOrNull() ?: return null
        val h2h = bookmaker.markets.find { it.key == "h2h" }
        val totals = bookmaker.markets.find { it.key == "totals" }

        val homeOdd = h2h?.outcomes?.find { it.name == event.homeTeam }?.price
        val awayOdd = h2h?.outcomes?.find { it.name == event.awayTeam }?.price
        val drawOdd = h2h?.outcomes?.find { it.name == "Draw" }?.price
        val over = totals?.outcomes?.find { it.name == "Over" }?.price
        val under = totals?.outcomes?.find { it.name == "Under" }?.price

        return OddsEntity(
            matchId = matchId,
            bookmaker = bookmaker.title,
            homeOdds = homeOdd,
            drawOdds = drawOdd,
            awayOdds = awayOdd,
            over25Odds = over,
            under25Odds = under,
            source = sourceName,
            lastUpdated = now
        )
    }

    private fun findEvent(
        events: List<OddsEventDto>,
        homeName: String,
        awayName: String
    ): OddsEventDto? {
        val home = normalize(homeName)
        val away = normalize(awayName)
        return events.firstOrNull { e ->
            val eh = normalize(e.homeTeam)
            val ea = normalize(e.awayTeam)
            (eh == home && ea == away) ||
                (eh.contains(home) && ea.contains(away)) ||
                (home.contains(eh) && away.contains(ea))
        }
    }

    private fun normalize(name: String): String =
        name.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun mapLeagueToSportKey(leagueId: Int): String = when (leagueId) {
        39 -> "soccer_epl"
        140 -> "soccer_spain_la_liga"
        135 -> "soccer_italy_serie_a"
        78 -> "soccer_germany_bundesliga"
        61 -> "soccer_france_ligue_one"
        2 -> "soccer_uefa_champs_league"
        else -> "soccer_epl"
    }
}

@Serializable
data class OddsEventDto(
    val id: String,
    @SerialName("home_team") val homeTeam: String,
    @SerialName("away_team") val awayTeam: String,
    val bookmakers: List<BookmakerDto> = emptyList()
)

@Serializable
data class BookmakerDto(
    val key: String,
    val title: String,
    val markets: List<MarketDto> = emptyList()
)

@Serializable
data class MarketDto(
    val key: String,
    val outcomes: List<OutcomeDto> = emptyList()
)

@Serializable
data class OutcomeDto(
    val name: String,
    val price: Double,
    val point: Double? = null
)
