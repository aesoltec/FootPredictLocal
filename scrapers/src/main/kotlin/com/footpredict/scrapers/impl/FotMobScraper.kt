package com.footpredict.scrapers.impl

import com.footpredict.scrapers.BaseHttpScraper
import com.footpredict.scrapers.Feasibility
import com.footpredict.scrapers.MatchKeys
import com.footpredict.scrapers.MatchMetadata
import com.footpredict.scrapers.Outcome
import com.footpredict.scrapers.ScraperPrediction

/**
 * FotMob — requête GET directe sur l’endpoint JSON interne (pas de HTML).
 *
 * HONNÊTETÉ CONTRACTUELLE (lire avant usage) :
 * - l’API exige l’en-tête `x-mas`, un jeton ROTATIF extrait du JS du site ;
 *   il expire sans préavis (alors : HTTP 403 → null, isolé par le runner) ;
 * - le token N’EST PAS codé en dur : variable d’environnement FOTMOB_X_MAS
 *   (rafraîchir : ouvrir fotmob.com → devtools → copier `x-mas` d’un appel
 *   `/api/…`, `export FOTMOB_X_MAS=…`) ;
 * - endpoints non contractuels : toute la réponse est parsée en défensif,
 *   champs manquants → null. Statut : PARTIAL, jamais de promesse de stabilité.
 */
class FotMobScraper(
    private val ccode: String = "ENG",
    private val xMas: String = System.getenv("FOTMOB_X_MAS") ?: ""
) : BaseHttpScraper() {

    override val sourceName = "fotmob"
    override val reliability = 0.55
    override val feasibility = Feasibility.PARTIAL

    override fun extraHeaders(): Map<String, String> = buildMap {
        put("Accept", "application/json")
        if (xMas.isNotBlank()) put("x-mas", xMas)
    }

    override suspend fun fetchData(matchId: String) = null // prédictions seules

    override suspend fun predict(metadata: MatchMetadata): ScraperPrediction? {
        if (xMas.isBlank()) return null // sans jeton frais : abstention propre
        val day = metadata.date.replace("-", "").takeIf { it.length == 8 } ?: return null
        val body = getText(
            "https://www.fotmob.com/api/matches?date=$day&ccode3=${ccode}_PC&timezone=UTC"
        )
        val match = findMatch(body, metadata.homeTeam, metadata.awayTeam) ?: return null
        return parsePrediction(match)
    }

    private fun findMatch(dayJson: String, home: String, away: String): String? {
        val wantedH = MatchKeys.normalize(home)
        val wantedA = MatchKeys.normalize(away)
        for (league in splitLeagues(dayJson)) {
            for (m in splitMatches(league)) {
                val h = extractTeam(m, "home") ?: continue
                val a = extractTeam(m, "away") ?: continue
                if (MatchKeys.normalize(h) == wantedH && MatchKeys.normalize(a) == wantedA) {
                    return m
                }
            }
        }
        return null
    }

    companion object {
        /** Découpe les blocs "matches":[...] d’une journée (défensif). */
        fun splitLeagues(dayJson: String): List<String> {
            val out = mutableListOf<String>()
            var i = 0
            while (true) {
                val k = dayJson.indexOf("\"matches\"", i)
                if (k < 0) break
                val start = dayJson.indexOf('[', k)
                if (start < 0) break
                var depth = 0
                var inStr = false
                var esc = false
                var j = start
                while (j < dayJson.length) {
                    val c = dayJson[j]
                    if (inStr) {
                        if (esc) esc = false
                        else if (c == '\\') esc = true
                        else if (c == '"') inStr = false
                    } else when (c) {
                        '"' -> inStr = true
                        '[' -> depth++
                        ']' -> {
                            depth--
                            if (depth == 0) {
                                out.add(dayJson.substring(start, j + 1))
                                break
                            }
                        }
                    }
                    j++
                }
                i = j + 1
            }
            return out
        }

        fun splitMatches(matchesArray: String): List<String> {
            // Retire les crochets externes puis découpe en objets.
            val inner = matchesArray.trim().removePrefix("[").removeSuffix("]")
            if (inner.isBlank()) return emptyList()
            return StatsBombScraper.splitTopLevelObjects("[$inner]")
        }

        /** Nom d’équipe home/away (plusieurs formes tolérées). */
        fun extractTeam(matchJson: String, side: String): String? {
            val key = if (side == "home") "homeTeam" else "awayTeam"
            StatsBombScraper.extractNested(matchJson, key, "name")?.let { return it }
            StatsBombScraper.extractNested(matchJson, key, "shortName")?.let { return it }
            StatsBombScraper.extractNested(matchJson, side, "name")?.let { return it }
            return null
        }

        /**
         * Pronostic depuis un objet match : cotes 1X2 si présentes
         * (implicites au prorata), sinon null (abstention).
         * Forme testée en unitaires sur payload échantillon, jamais garantie
         * contre les changements FotMob.
         */
        fun parsePrediction(matchJson: String, source: String = "fotmob", weight: Double = 0.55): ScraperPrediction? {
            val h = Regex(""""(?:homeOdds|oddsHome|home)"\s*:\s*"?([\d.]+)"?""").find(matchJson)?.groupValues?.get(1)?.toDoubleOrNull()
            val d = Regex(""""(?:drawOdds|oddsDraw|draw)"\s*:\s*"?([\d.]+)"?""").find(matchJson)?.groupValues?.get(1)?.toDoubleOrNull()
            val a = Regex(""""(?:awayOdds|oddsAway|away)"\s*:\s*"?([\d.]+)"?""").find(matchJson)?.groupValues?.get(1)?.toDoubleOrNull()
            val implied = FootballDataScraper.impliedProbabilities(h, d, a) ?: return null
            val (outcome, probability) = maxOf(
                Outcome.HOME to implied.first,
                Outcome.DRAW to implied.second,
                Outcome.AWAY to implied.third,
                compareBy { it.second }
            )
            return ScraperPrediction(source, outcome, probability, h, d, a, weight = weight)
        }
    }
}
