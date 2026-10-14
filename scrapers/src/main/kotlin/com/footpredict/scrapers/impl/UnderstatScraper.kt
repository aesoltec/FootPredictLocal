package com.footpredict.scrapers.impl

import com.footpredict.scrapers.BaseHttpScraper
import com.footpredict.scrapers.Feasibility
import com.footpredict.scrapers.MatchData
import com.footpredict.scrapers.MatchKeys
import com.footpredict.scrapers.MatchMetadata
import com.footpredict.scrapers.Outcome
import com.footpredict.scrapers.ScraperPrediction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Understat — double mode :
 * 1. endpoint JSON interne `getLeagueData` (PRIMAIRE, vérifié le 2026-10-09) ;
 * 2. fallback legacy : extraction Regex du JSON masqué dans les balises
 *    `<script>` (`datesData = JSON.parse('…')`, encodage \xNN).
 *
 * `predict()` exploite le champ `forecast` {w,d,l} publié par Understat
 * lui-même (probabilités maison — signal réel, pas calculé par nous).
 *
 * @param leagueSlug ex. "EPL", "La_liga", "Serie_A", "Bundesliga", "Ligue_1".
 * @param season année de démarrage (2026 pour 2026/27).
 */
class UnderstatScraper(
    private val leagueSlug: String = "EPL",
    private val season: Int = 2026
) : BaseHttpScraper() {

    override val sourceName = "understat"
    override val reliability = 0.9
    override val feasibility = Feasibility.SUPPORTED

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun fetchData(matchId: String): MatchData? {
        val (home, away, date) = MatchKeys.parse(matchId) ?: return null
        val found = findMatch(home, away, MatchKeys.parseDateToEpoch(date)) ?: return null
        val (o, h, a) = found
        val isResult = o["isResult"]?.jsonPrimitive?.content == "True" ||
            o["isResult"]?.toString() == "true"
        val goals = o["goals"]?.jsonObject
        val xg = o["xG"]?.jsonObject
        return MatchData(
            homeTeam = h,
            awayTeam = a,
            dateTimestamp = parseUnderstatDate(o["datetime"]?.jsonPrimitive?.content),
            status = if (isResult) "FT" else "NS",
            scoreHome = goals?.get("h")?.jsonPrimitive?.content?.toIntOrNull(),
            scoreAway = goals?.get("a")?.jsonPrimitive?.content?.toIntOrNull(),
            xgHome = xg?.get("h")?.jsonPrimitive?.content?.toDoubleOrNull(),
            xgAway = xg?.get("a")?.jsonPrimitive?.content?.toDoubleOrNull(),
            source = sourceName
        )
    }

    override suspend fun predict(metadata: MatchMetadata): ScraperPrediction? {
        val found = findMatch(
            metadata.homeTeam, metadata.awayTeam,
            MatchKeys.parseDateToEpoch(metadata.date)
        ) ?: return null
        val (o, h, a) = found
        val fc = o["forecast"]?.jsonObject ?: return null
        val w = fc["w"]?.jsonPrimitive?.content?.toDoubleOrNull()
        val d = fc["d"]?.jsonPrimitive?.content?.toDoubleOrNull()
        val l = fc["l"]?.jsonPrimitive?.content?.toDoubleOrNull()
        if (w == null || d == null || l == null) return null
        val outcome = when (maxOf(w, d, l)) {
            w -> Outcome.HOME
            l -> Outcome.AWAY
            else -> Outcome.DRAW
        }
        return ScraperPrediction(
            source = "$sourceName-forecast",
            outcome = outcome,
            confidence = maxOf(w, d, l).coerceIn(0.0, 1.0),
            weight = reliability
        )
    }

    /** Triple (objet, nom domicile, nom extérieur) ou null. */
    private suspend fun findMatch(
        home: String,
        away: String,
        targetDate: Long
    ): Triple<JsonObject, String, String>? {
        val wantedH = MatchKeys.normalize(home)
        val wantedA = MatchKeys.normalize(away)
        for (el in fetchDatesArray()) {
            val o = el.jsonObject
            val h = o["h"]?.jsonObject?.get("title")?.jsonPrimitive?.content ?: continue
            val a = o["a"]?.jsonObject?.get("title")?.jsonPrimitive?.content ?: continue
            if (MatchKeys.normalize(h) != wantedH || MatchKeys.normalize(a) != wantedA) continue
            val dt = parseUnderstatDate(o["datetime"]?.jsonPrimitive?.content)
            if (targetDate != 0L && dt != 0L && kotlin.math.abs(dt - targetDate) > 3 * 86400) continue
            return Triple(o, h, a)
        }
        return null
    }

    /** XHR d’abord, legacy ensuite ; liste vide si tout échoue. */
    private suspend fun fetchDatesArray(): List<kotlinx.serialization.json.JsonElement> {
        runCatching {
            val body = getText(
                "https://understat.com/getLeagueData/$leagueSlug/$season",
                referer = "https://understat.com/league/$leagueSlug/$season"
            )
            val arr = json.parseToJsonElement(body).jsonObject["dates"]?.jsonArray
            if (arr != null && arr.isNotEmpty()) return arr.toList()
        }
        runCatching {
            val html = getText("https://understat.com/league/$leagueSlug/$season")
            val raw = extractLegacyDatesData(html) ?: return emptyList()
            val el = json.parseToJsonElement(raw)
            val arr = try {
                el.jsonArray
            } catch (_: Exception) {
                el.jsonObject["dates"]?.jsonArray
            }
            if (arr != null && arr.isNotEmpty()) return arr.toList()
        }
        return emptyList()
    }

    companion object {
        /** "2025-08-15 19:00:00" → epoch UTC (pas de fuseau exposé). */
        fun parseUnderstatDate(raw: String?): Long {
            if (raw.isNullOrBlank()) return 0L
            val m = Regex("""(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})(?::(\d{2}))?""").find(raw)
                ?: return 0L
            val (y, mo, d, h, mi, s) = m.destructured
            return java.time.LocalDateTime.of(
                y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt(),
                if (s.isEmpty()) 0 else s.toInt()
            ).toEpochSecond(java.time.ZoneOffset.UTC)
        }

        /**
         * Fallback legacy : `datesData = JSON.parse('…')` avec échappements \xNN.
         * Retourne le JSON décodé ou null.
         */
        fun extractLegacyDatesData(html: String): String? {
            val m = Regex("""datesData\s*=\s*JSON\.parse\('((?:[^'\\]|\\.)*)'\)""").find(html)
                ?: return null
            return m.groupValues[1].replace(Regex("""\\x([0-9a-fA-F]{2})""")) {
                String(intArrayOf(it.groupValues[1].toInt(16)), 0, 1)
            }
        }
    }
}
