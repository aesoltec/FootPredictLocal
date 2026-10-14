package com.footpredict.scrapers.impl

import com.footpredict.scrapers.BaseHttpScraper
import com.footpredict.scrapers.Feasibility
import com.footpredict.scrapers.MatchData
import com.footpredict.scrapers.MatchKeys
import com.footpredict.scrapers.MatchMetadata
import com.footpredict.scrapers.Outcome
import com.footpredict.scrapers.ScraperPrediction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The Odds API v4 — cotes officielles bookmakers, clé GRATUITE 500 crédits/mois
 * (jamais codée en dur : paramètre ou `ODDS_API_KEY`).
 *
 * Fournit : 1X2 (h2h) + Over/Under (totals, point 2.5) → le signal Marché qui
 * affine l’indice de confiance dans `aggregateMarkets` (poids 0.85).
 *
 * Budget : 1 crédit par appel (regions=eu unique). Avec 6 ligues, UN appel
 * par ligue et par jour ≈ 180 crédits/mois. Dépasser = `QuotaExhausted`.
 * Headers lus : `x-requests-remaining`, `x-requests-used`.
 */
class OddsApiScraper(
    private val apiKey: String = System.getenv("ODDS_API_KEY") ?: ""
) : BaseHttpScraper() {

    override val sourceName = "the-odds-api"
    override val reliability = 0.85
    override val feasibility = Feasibility.SUPPORTED

    /** Dernier quota observé (restant), -1 si inconnu. */
    var lastQuotaRemaining: Int = -1
        private set

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        /** Ligues de l’app → sport keys The Odds API. */
        val SPORTS = mapOf(
            39 to "soccer_epl",
            140 to "soccer_spain_la_liga",
            135 to "soccer_italy_serie_a",
            78 to "soccer_germany_bundesliga",
            61 to "soccer_france_ligue_one",
            2 to "soccer_uefa_champs_league"
        )

        /**
         * Événement résolu : cotes 1X2 + Over/Under 2.5 du premier bookmaker.
         * Le multi-book (moyenne) est une extension documentée, pas implémentée :
         * le premier bookmaker suffit pour un signal stable et économe.
         */
        data class ResolvedOdds(
            val homeTeam: String,
            val awayTeam: String,
            val commenceEpoch: Long,
            val bookmaker: String,
            val home: Double?,
            val draw: Double?,
            val away: Double?,
            val over25: Double?,
            val under25: Double?
        )

        fun parseEvents(body: String): List<ResolvedOdds> {
            val out = mutableListOf<ResolvedOdds>()
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            val arr = try {
                json.parseToJsonElement(body).jsonArray
            } catch (_: Exception) {
                return out
            }
            for (el in arr) {
                val o = el.jsonObject
                val home = o["home_team"]?.jsonPrimitive?.content ?: continue
                val away = o["away_team"]?.jsonPrimitive?.content ?: continue
                val commence = try {
                    java.time.Instant.parse(o["commence_time"]?.jsonPrimitive?.content ?: "").epochSecond
                } catch (_: Exception) {
                    0L
                }
                val book = o["bookmakers"]?.jsonArray?.firstOrNull()?.jsonObject ?: continue
                var h: Double? = null
                var d: Double? = null
                var a: Double? = null
                var over: Double? = null
                var under: Double? = null
                for (m in book["markets"]?.jsonArray ?: emptyList()) {
                    val market = m.jsonObject
                    val outcomes = market["outcomes"]?.jsonArray ?: continue
                    when (market["key"]?.jsonPrimitive?.content) {
                        "h2h" -> for (oc in outcomes) {
                            val oo = oc.jsonObject
                            val price = oo["price"]?.jsonPrimitive?.doubleOrNull ?: continue
                            when (oo["name"]?.jsonPrimitive?.content) {
                                home -> h = price
                                "Draw" -> d = price
                                away -> a = price
                            }
                        }
                        "totals" -> for (oc in outcomes) {
                            val oo = oc.jsonObject
                            if ((oo["point"]?.jsonPrimitive?.doubleOrNull ?: -1.0) != 2.5) continue
                            val price = oo["price"]?.jsonPrimitive?.doubleOrNull ?: continue
                            when (oo["name"]?.jsonPrimitive?.content) {
                                "Over" -> over = price
                                "Under" -> under = price
                            }
                        }
                    }
                }
                out.add(
                    ResolvedOdds(
                        homeTeam = home, awayTeam = away, commenceEpoch = commence,
                        bookmaker = book["title"]?.jsonPrimitive?.content ?: "?",
                        home = h, draw = d, away = a, over25 = over, under25 = under
                    )
                )
            }
            return out
        }

        fun parseQuotaRemaining(headers: Map<String, List<String>>): Int =
            headers.entries
                .firstOrNull { it.key.equals("x-requests-remaining", ignoreCase = true) }
                ?.value?.firstOrNull()?.toIntOrNull() ?: -1
    }

    override fun onResponseHeaders(headers: Map<String, List<String>>) {
        val remaining = parseQuotaRemaining(headers)
        if (remaining >= 0) lastQuotaRemaining = remaining
        if (remaining == 0) throw QuotaExhaustedException()
    }

    private fun requireKey() {
        if (apiKey.isBlank()) throw IllegalStateException("ODDS_API_KEY manquante")
    }

    private suspend fun leagueEvents(sportKey: String): List<ResolvedOdds> {
        requireKey()
        val body = getText(
            "https://api.the-odds-api.com/v4/sports/$sportKey/odds" +
                "?apiKey=$apiKey&regions=eu&markets=h2h,totals&oddsFormat=decimal"
        )
        return parseEvents(body)
    }

    private suspend fun resolve(
        home: String,
        away: String,
        targetDate: Long
    ): ResolvedOdds? {
        val wantedH = MatchKeys.normalize(home)
        val wantedA = MatchKeys.normalize(away)
        for ((_, sport) in SPORTS) {
            val events = try {
                leagueEvents(sport)
            } catch (_: Exception) {
                continue
            }
            for (e in events) {
                if (MatchKeys.normalize(e.homeTeam) != wantedH) continue
                if (MatchKeys.normalize(e.awayTeam) != wantedA) continue
                if (targetDate != 0L && e.commenceEpoch != 0L &&
                    kotlin.math.abs(e.commenceEpoch - targetDate) > 3 * 86400
                ) continue
                return e
            }
        }
        return null
    }

    override suspend fun fetchData(matchId: String): MatchData? {
        val (home, away, date) = MatchKeys.parse(matchId) ?: return null
        val e = resolve(home, away, MatchKeys.parseDateToEpoch(date)) ?: return null
        return MatchData(
            homeTeam = e.homeTeam,
            awayTeam = e.awayTeam,
            dateTimestamp = e.commenceEpoch,
            status = "NS",
            oddsHome = e.home,
            oddsDraw = e.draw,
            oddsAway = e.away,
            source = "$sourceName:${e.bookmaker}"
        )
    }

    override suspend fun predict(metadata: MatchMetadata): ScraperPrediction? {
        val e = resolve(
            metadata.homeTeam, metadata.awayTeam,
            MatchKeys.parseDateToEpoch(metadata.date)
        ) ?: return null
        val implied = FootballDataScraper.impliedProbabilities(e.home, e.draw, e.away)
            ?: return null
        val (outcome, probability) = maxOf(
            Outcome.HOME to implied.first,
            Outcome.DRAW to implied.second,
            Outcome.AWAY to implied.third,
            compareBy { it.second }
        )
        val over = e.over25?.takeIf { it > 1.0 }
        val under = e.under25?.takeIf { it > 1.0 }
        val overProb = if (over != null && under != null) over.let { o ->
            val m = 1.0 / o + 1.0 / under
            (1.0 / o) / m
        } else null
        return ScraperPrediction(
            source = sourceName,
            outcome = outcome,
            confidence = probability,
            homeOdds = e.home,
            drawOdds = e.draw,
            awayOdds = e.away,
            over25Prob = overProb,
            weight = reliability
        )
    }

    class QuotaExhaustedException :
        Exception("quota The Odds API épuisé (500/mois) — prochain reset le 1er")
}
