package com.footpredict.scrapers.impl

import com.footpredict.scrapers.BaseHttpScraper
import com.footpredict.scrapers.Feasibility
import com.footpredict.scrapers.MatchData
import com.footpredict.scrapers.MatchKeys

/**
 * Football-Data.co.uk — CSV gratuits sans inscription : résultats historiques
 * + cotes de CLÔTURE (Pinnacle/Bet365). Pas de matchs à venir.
 * Les cotes de clôture alimentent le modèle Marché côté app.
 *
 * matchId : "HOME|AWAY|AAAA-MM-JJ" ; [divisions] ex. "E0" (PL), "SP1", "I1", "D1", "F1".
 */
class FootballDataUkScraper(
    private val divisions: List<String> = listOf("E0", "SP1", "I1", "D1", "F1"),
    private val seasonCode: String = "2526"
) : BaseHttpScraper() {

    override val sourceName = "football-data.co.uk"
    override val reliability = 0.88
    override val feasibility = Feasibility.SUPPORTED

    override suspend fun fetchData(matchId: String): MatchData? {
        val (home, away, date) = MatchKeys.parse(matchId) ?: return null
        val targetDate = MatchKeys.parseDateToEpoch(date)
        val wantedH = MatchKeys.normalize(home)
        val wantedA = MatchKeys.normalize(away)

        for (div in divisions) {
            val csv = try {
                getText("https://www.football-data.co.uk/mmz4281/$seasonCode/$div.csv")
            } catch (_: Exception) {
                continue
            }
            val rows = parseCsv(csv)
            if (rows.isEmpty()) continue
            val header = rows.first()
            val iDate = header.indexOf("Date")
            val iHome = header.indexOf("HomeTeam")
            val iAway = header.indexOf("AwayTeam")
            val iHg = header.indexOf("FTHG")
            val iAg = header.indexOf("FTAG")
            val iH = header.indexOf("B365H").takeIf { it >= 0 } ?: header.indexOf("PSH")
            val iD = header.indexOf("B365D").takeIf { it >= 0 } ?: header.indexOf("PSD")
            val iA = header.indexOf("B365A").takeIf { it >= 0 } ?: header.indexOf("PSA")
            if (iDate < 0 || iHome < 0 || iAway < 0) continue

            for (row in rows.drop(1)) {
                fun col(i: Int): String? = row.getOrNull(i)?.takeIf { it.isNotBlank() }
                if (col(iHome)?.let { MatchKeys.normalize(it) } != wantedH) continue
                if (col(iAway)?.let { MatchKeys.normalize(it) } != wantedA) continue
                val dt = MatchKeys.parseDateToEpoch(col(iDate))
                if (targetDate != 0L && dt != 0L && kotlin.math.abs(dt - targetDate) > 3 * 86400) continue
                return MatchData(
                    homeTeam = col(iHome)!!,
                    awayTeam = col(iAway)!!,
                    dateTimestamp = dt,
                    status = if (col(iHg) != null && col(iAg) != null) "FT" else "NS",
                    scoreHome = col(iHg)?.toIntOrNull(),
                    scoreAway = col(iAg)?.toIntOrNull(),
                    oddsHome = iH.takeIf { it >= 0 }?.let { col(it)?.toDoubleOrNull() },
                    oddsDraw = iD.takeIf { it >= 0 }?.let { col(it)?.toDoubleOrNull() },
                    oddsAway = iA.takeIf { it >= 0 }?.let { col(it)?.toDoubleOrNull() },
                    source = sourceName
                )
            }
        }
        return null
    }

    companion object {
        /**
         * Découpe CSV en respectant les champs entre guillemets
         * (les fichiers football-data.co.uk sont simples mais on ne suppose rien).
         */
        fun parseCsv(text: String): List<List<String>> =
            text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { splitLine(it) }
                .toList()

        fun splitLine(line: String): List<String> {
            val out = mutableListOf<String>()
            val cur = StringBuilder()
            var inQuotes = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    c == '"' -> {
                        if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                            cur.append('"'); i++
                        } else inQuotes = !inQuotes
                    }
                    c == ',' && !inQuotes -> {
                        out.add(cur.toString().trim()); cur.clear()
                    }
                    else -> cur.append(c)
                }
                i++
            }
            out.add(cur.toString().trim())
            return out
        }
    }
}
