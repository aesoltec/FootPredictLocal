package com.footpredict.scrapers.impl

import com.footpredict.scrapers.BaseHttpScraper
import com.footpredict.scrapers.Feasibility
import com.footpredict.scrapers.MatchData
import com.footpredict.scrapers.MatchKeys
import com.footpredict.scrapers.TeamStats

/**
 * SoccerStats — tables HTML simples (`table#btable`, lignes `trow*`).
 * Page récupérable en HTTP pur (sonde 2026-10-12, HTTP 200, structure
 * confirmée). Sélecteurs NON vérifiés de bout en bout : le parsing est
 * défensif (lignes incomprises ignorées, null si rien d’exploitable).
 *
 * @param leagueSlug ex. "england", "spain", "italy", "germany", "france".
 */
class SoccerStatsScraper(
    private val leagueSlug: String = "england"
) : BaseHttpScraper() {

    override val sourceName = "soccerstats"
    override val reliability = 0.6
    override val feasibility = Feasibility.PARTIAL

    override suspend fun fetchData(matchId: String): MatchData? {
        val (home, away, _) = MatchKeys.parse(matchId) ?: return null
        val html = getText("https://www.soccerstats.com/homeaway.asp?league=$leagueSlug")
        val table = extractHomeAwayTable(html, home, away) ?: return null
        return MatchData(
            homeTeam = home,
            awayTeam = away,
            status = "NS",
            statsHome = table.first,
            statsAway = table.second,
            source = sourceName
        )
    }

    companion object {
        /**
         * Extrait (statsDom, statsExt) pour les deux équipes depuis le HTML.
         * Stratégie : lignes `<tr>` contenant le nom d’équipe (insensible à la
         * casse, balises retirées), puis lecture des 6 premiers entiers de la
         * ligne comme J/G/N/P/BP/BC (ordre usuel du site ; si absent → null).
         */
        fun extractHomeAwayTable(
            html: String,
            home: String,
            away: String
        ): Pair<TeamStats, TeamStats>? {
            val homeRow = findTeamRow(html, home) ?: return null
            val awayRow = findTeamRow(html, away) ?: return null
            val hs = rowToStats(homeRow) ?: return null
            val aws = rowToStats(awayRow) ?: return null
            return hs to aws
        }

        fun findTeamRow(html: String, team: String): String? {
            val wanted = MatchKeys.normalize(team)
            if (wanted.isEmpty()) return null
            val rows = Regex("""<tr[^>]*>(.*?)</tr>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(html)
                .map { it.groupValues[1] }
            for (row in rows) {
                val text = row.replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                if (MatchKeys.normalize(text).contains(wanted)) return row
            }
            return null
        }

        fun rowToStats(rowHtml: String): TeamStats? {
            val text = rowHtml.replace(Regex("<[^>]+>"), " ")
            val nums = Regex("""\d+""").findAll(text).map { it.value.toInt() }.toList()
            if (nums.size < 6) return null
            return TeamStats(
                played = nums[0], won = nums[1], drawn = nums[2], lost = nums[3],
                goalsFor = nums[4], goalsAgainst = nums[5]
            )
        }
    }
}
