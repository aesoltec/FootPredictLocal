package com.footpredict.scrapers.impl

import com.footpredict.scrapers.BaseHttpScraper
import com.footpredict.scrapers.Feasibility
import com.footpredict.scrapers.MatchData
import com.footpredict.scrapers.MatchKeys
import com.footpredict.scrapers.MatchMetadata
import com.footpredict.scrapers.Outcome
import com.footpredict.scrapers.ScraperPrediction
import com.footpredict.scrapers.TeamStats
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * football-data.org — API officielle, clé GRATUITE (jamais codée en dur :
 * paramètre ou `FOOTBALL_DATA_KEY`). Compétitions du plan gratuit :
 * WC, CL, BL1, DED, BSA, PD, FL1, ELC, PPL, EC, SA, PL.
 *
 * Fournit : fixtures en direct (scores live inclus) + CLASSEMENTS
 * (joués, victoires, nuls, défaites, buts, forme) → alimente à la fois les
 * fixtures et les stats d’équipe du PredictionEngine.
 *
 * Quota gratuit : 10 req/min — voir [checkThrottle] (l’API le rappelle
 * dans les headers ; on s’auto-limite au lieu de heurter le rateLimiter).
 */
class FootballDataOrgScraper(
    private val apiKey: String = System.getenv("FOOTBALL_DATA_KEY") ?: ""
) : BaseHttpScraper() {

    override val sourceName = "football-data.org"
    override val reliability = 0.78
    override val feasibility = Feasibility.SUPPORTED

    /** Dernier quota observé (disponible/minute), -1 si inconnu. */
    var lastQuotaAvailableMinute: Int = -1
        private set

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        /** Ligues de l’app (référentiel API-Football) → codes FD. */
        val COMPETITIONS = mapOf(
            39 to "PL", 140 to "PD", 135 to "SA", 78 to "BL1", 61 to "FL1",
            88 to "DED", 94 to "PPL", 41 to "ELC", 2 to "CL"
        )

        /**
         * Le fournisseur demande de respecter les headers de throttling,
         * sinon bannissement du rateLimiter. Si le quota minute est épuisé,
         * on lève [QuotaExhaustedException] (isolée par le runner).
         */
        fun checkThrottle(headers: Map<String, List<String>>) {
            val available = headers.entries
                .firstOrNull { it.key.equals("x-requests-available-minute", ignoreCase = true) }
                ?.value?.firstOrNull()?.toIntOrNull()
            if (available != null && available <= 1) throw QuotaExhaustedException(available)
        }

        /** "2025-10-09T15:00:00Z" → epoch secondes. */
        fun parseFdDate(raw: String?): Long {
            if (raw.isNullOrBlank()) return 0L
            return try {
                java.time.Instant.parse(raw.trim()).epochSecond
            } catch (_: Exception) {
                0L
            }
        }

        fun mapStatus(status: String?): String = when (status?.uppercase()) {
            "SCHEDULED", "TIMED" -> "NS"
            "IN_PLAY", "LIVE" -> "LIVE"
            "PAUSED" -> "HT"
            "FINISHED", "AWARDED" -> "FT"
            "POSTPONED" -> "PST"
            "CANCELLED", "SUSPENDED" -> "CANC"
            else -> status ?: "NS"
        }

        /**
         * Standings → nom canonique → TeamStats (totaux saison ; la forme
         * "W,D,L,W,W" est convertie en liste, plus récente en dernier).
         */
        fun parseStandings(body: String): Map<String, TeamStats> {
            val out = mutableMapOf<String, TeamStats>()
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            val root = json.parseToJsonElement(body).jsonObject
            val tables = root["standings"]?.jsonArray ?: return out
            val table = tables.firstOrNull()?.jsonObject ?: return out
            for (el in table["table"]?.jsonArray ?: return out) {
                val o = el.jsonObject
                val name = o["team"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: continue
                val form = o["form"]?.jsonPrimitive?.content
                    ?.split(",")?.mapNotNull { it.trim().firstOrNull() }
                    ?.filter { it == 'W' || it == 'D' || it == 'L' }
                    ?: emptyList()
                out[com.footpredict.scrapers.TeamAliases.canonical(name)] = TeamStats(
                    played = o["playedGames"]?.jsonPrimitive?.intOrNull ?: 0,
                    won = o["won"]?.jsonPrimitive?.intOrNull ?: 0,
                    drawn = o["draw"]?.jsonPrimitive?.intOrNull ?: 0,
                    lost = o["lost"]?.jsonPrimitive?.intOrNull ?: 0,
                    goalsFor = o["goalsFor"]?.jsonPrimitive?.intOrNull ?: 0,
                    goalsAgainst = o["goalsAgainst"]?.jsonPrimitive?.intOrNull ?: 0,
                    form = form
                )
            }
            return out
        }
    }

    override fun extraHeaders(): Map<String, String> =
        mapOf("X-Auth-Token" to apiKey)

    override fun onResponseHeaders(headers: Map<String, List<String>>) {
        headers.entries
            .firstOrNull { it.key.equals("x-requests-available-minute", ignoreCase = true) }
            ?.value?.firstOrNull()?.toIntOrNull()?.let { lastQuotaAvailableMinute = it }
        checkThrottle(headers)
    }

    private suspend fun get(path: String, params: Map<String, String> = emptyMap()): String {
        if (apiKey.isBlank()) throw IllegalStateException("FOOTBALL_DATA_KEY manquante")
        val query = params.entries.joinToString("&") { "${it.key}=${it.value}" }
        val url = "https://api.football-data.org/v4$path" + (if (query.isEmpty()) "" else "?$query")
        return getText(url)
    }

    override suspend fun fetchData(matchId: String): MatchData? {
        val (home, away, date) = MatchKeys.parse(matchId) ?: return null
        val targetDate = MatchKeys.parseDateToEpoch(date)
        for ((_, code) in COMPETITIONS) {
            val body = try {
                get("/competitions/$code/matches")
            } catch (_: Exception) {
                continue
            }
            val found = findFixture(body, home, away, targetDate) ?: continue
            return found
        }
        return null
    }

    /** Classement d’une compétition → stats par équipe (noms canoniques). */
    suspend fun fetchStandings(leagueId: Int, season: Int): Map<String, TeamStats> {
        val code = COMPETITIONS[leagueId] ?: return emptyMap()
        val body = get("/competitions/$code/standings", mapOf("season" to season.toString()))
        return parseStandings(body)
    }

    override suspend fun predict(metadata: MatchMetadata): ScraperPrediction? {
        // football-data.org ne publie aucun pronostic : abstention (données oui, avis non).
        return null
    }

    private fun findFixture(body: String, home: String, away: String, targetDate: Long): MatchData? {
        val wantedH = MatchKeys.normalize(home)
        val wantedA = MatchKeys.normalize(away)
        val root = json.parseToJsonElement(body).jsonObject
        for (el in root["matches"]?.jsonArray ?: return null) {
            val o = el.jsonObject
            val h = o["homeTeam"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: continue
            val a = o["awayTeam"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: continue
            if (!namesMatch(h, wantedH) || !namesMatch(a, wantedA)) continue
            val dt = parseFdDate(o["utcDate"]?.jsonPrimitive?.content)
            if (targetDate != 0L && dt != 0L && kotlin.math.abs(dt - targetDate) > 3 * 86400) continue
            val status = mapStatus(o["status"]?.jsonPrimitive?.content)
            val score = o["score"]?.jsonObject?.get("fullTime")?.jsonObject
            return MatchData(
                homeTeam = h,
                awayTeam = a,
                dateTimestamp = dt,
                status = status,
                scoreHome = score?.get("home")?.jsonPrimitive?.content?.toIntOrNull(),
                scoreAway = score?.get("away")?.jsonPrimitive?.content?.toIntOrNull(),
                source = sourceName
            )
        }
        return null
    }

    private fun namesMatch(candidate: String, wantedNormalized: String): Boolean {
        val c = MatchKeys.normalize(candidate)
        return c == wantedNormalized ||
            (c.length > 3 && wantedNormalized.length > 3 &&
                (c.contains(wantedNormalized) || wantedNormalized.contains(c)))
    }

    class QuotaExhaustedException(val available: Int) :
        Exception("quota football-data.org épuisé (disponible=$available)")
}
