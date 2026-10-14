package com.footpredict.scrapers.impl

import com.footpredict.scrapers.BaseHttpScraper
import com.footpredict.scrapers.Feasibility
import com.footpredict.scrapers.MatchData
import com.footpredict.scrapers.MatchKeys

/**
 * StatsBomb Open Data (GitHub, gratuit, sans clé) : données ÉVÉNEMENTIELLES
 * historiques (matchs terminés uniquement — pas de à-venir, pas de pronos).
 * Contribue des scores vérifiés au moteur ; la modélisation reste côté app.
 *
 * matchId : "HOME|AWAY|AAAA-MM-JJ" résolu dans competition/season donnés.
 *
 * @param competitionId ex. 43 (World Cup), 2 (Premier League).
 * @param seasonId ex. 3 (saison 2018… voir open-data).
 */
class StatsBombScraper(
    private val competitionId: Int = 2,
    private val seasonId: Int = 106
) : BaseHttpScraper() {

    override val sourceName = "statsbomb"
    override val reliability = 0.85
    override val feasibility = Feasibility.SUPPORTED

    override suspend fun fetchData(matchId: String): MatchData? {
        val (home, away, date) = MatchKeys.parse(matchId) ?: return null
        val targetDate = MatchKeys.parseDateToEpoch(date)
        val body = getText(
            "https://raw.githubusercontent.com/statsbomb/open-data/master/data/matches/$competitionId/$seasonId.json"
        )
        val wantedH = MatchKeys.normalize(home)
        val wantedA = MatchKeys.normalize(away)
        // Extraction ciblée sans modèle complet : le schéma open-data est stable
        // mais volumineux ; on ne lit que les champs nécessaires.
        for (chunk in splitTopLevelObjects(body)) {
            val h = extractNested(chunk, "home_team", "home_team_name") ?: continue
            val a = extractNested(chunk, "away_team", "away_team_name") ?: continue
            if (MatchKeys.normalize(h) != wantedH || MatchKeys.normalize(a) != wantedA) continue
            val dt = MatchKeys.parseDateToEpoch(extract(chunk, "match_date"))
            if (targetDate != 0L && dt != 0L && kotlin.math.abs(dt - targetDate) > 3 * 86400) continue
            val hs = extract(chunk, "home_score")?.toIntOrNull()
            val aws = extract(chunk, "away_score")?.toIntOrNull()
            return MatchData(
                homeTeam = h,
                awayTeam = a,
                dateTimestamp = dt,
                status = if (hs != null && aws != null) "FT" else "NS",
                scoreHome = hs,
                scoreAway = aws,
                source = sourceName
            )
        }
        return null
    }

    companion object {
        /** Découpe un tableau JSON racine en objets de premier niveau (sans parseur complet). */
        fun splitTopLevelObjects(arrayJson: String): List<String> {
            val out = mutableListOf<String>()
            var depth = 0
            var start = -1
            var inString = false
            var escape = false
            for (i in arrayJson.indices) {
                val c = arrayJson[i]
                if (inString) {
                    if (escape) escape = false
                    else if (c == '\\') escape = true
                    else if (c == '"') inString = false
                    continue
                }
                when (c) {
                    '"' -> inString = true
                    '{' -> {
                        if (depth == 0) start = i
                        depth++
                    }
                    '}' -> {
                        depth--
                        if (depth == 0 && start >= 0) {
                            out.add(arrayJson.substring(start, i + 1))
                            start = -1
                        }
                    }
                }
            }
            return out
        }

        /** Extrait "key": "valeur" ou "key": nombre au premier niveau de l’objet. */
        fun extract(obj: String, key: String): String? {
            val m = Regex(""""$key"\s*:\s*("([^"\\]|\\.)*"|-?\d+(\.\d+)?)""").find(obj)
                ?: return null
            return m.groupValues[1].trim('"')
        }

        /** Extrait "parent": {... "child": valeur ...} (une imbrication). */
        fun extractNested(obj: String, parent: String, child: String): String? {
            val m = Regex(""""$parent"\s*:\s*\{([^{}]*)\}""").find(obj) ?: return null
            return extract(m.groupValues[1], child)
        }
    }
}
