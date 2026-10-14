package com.footpredict.scrapers

import com.footpredict.scrapers.impl.FootballDataScraper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Démo exécutable : `./gradlew :scrapers:run`.
 * Signaux SIMULÉS mais réalistes (mêmes formes que les parseurs produisent) ;
 * le calcul, lui, est le vrai [PredictionEngine.aggregateMarkets].
 */
fun main() {
    val meta = MatchMetadata("Paris Saint-Germain", "Marseille", "2026-10-17")
    val engine = PredictionEngine()

    // Signaux tels que les scrapers les produiraient (voir tests unitaires
    // pour les formes exactes parsées : forecast Understat, x-mas FotMob,
    // clôture Football-Data, h2h+totals Odds API).
    val statsOnly = listOf(
        // Understat forecast maison : w=0.55
        com.footpredict.scrapers.ScraperPrediction(
            "understat-forecast", Outcome.HOME, 0.55, weight = 0.9
        ),
        // FotMob : cotes internes → implicite HOME 0.52
        com.footpredict.scrapers.ScraperPrediction(
            "fotmob", Outcome.HOME, 0.52, weight = 0.55
        ),
        // Football-Data (clôture) : D 0.36 en tête
        com.footpredict.scrapers.ScraperPrediction(
            "football-data.co.uk", Outcome.DRAW, 0.36,
            over25Prob = 0.55, weight = 0.88
        )
    )
    // The Odds API : implicite HOME 0.58 + Over 0.61 (poids 0.85).
    val market = com.footpredict.scrapers.ScraperPrediction(
        "the-odds-api", Outcome.HOME, 0.58, over25Prob = 0.61, weight = 0.85
    )

    val json = Json { prettyPrint = true }
    fun dump(title: String, c: PredictionEngine.MarketConsensus) {
        println("=== $title ===")
        println(json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            buildJsonObject {
                put("match", "${meta.homeTeam} vs ${meta.awayTeam} (${meta.date})")
                put("pick_1x2", c.outcome1X2.side.name)
                put("indice_confiance_pct", round1(c.outcome1X2.confidencePct))
                c.overUnder?.let {
                    put("marche_ou", it.side.name)
                    put("confiance_ou_pct", round1(it.confidencePct))
                }
                putJsonObject("tendance") {
                    Outcome.entries.forEach { put(it.name, round1((c.trendByOutcome[it] ?: 0.0) * 100)) }
                }
                putJsonArray("sources") { c.sourcesUsed.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
                putJsonArray("avertissements") { c.warnings.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
            }
        ))
    }

    val avant = engine.aggregateMarkets(meta, statsOnly)!!
    dump("SANS marché (stats seules)", avant)
    val apres = engine.aggregateMarkets(meta, statsOnly + market)!!
    dump("AVEC marché (poids bookmaker 0.85)", apres)

    // Clôture → implicites : preuve du calcul qui alimente le marché.
    val implied = FootballDataScraper.impliedProbabilities(2.1, 3.4, 3.5)!!
    println("=== Clôture 2.10/3.40/3.50 → implicites ===")
    println("HOME=${pct(implied.first)} DRAW=${pct(implied.second)} AWAY=${pct(implied.third)}")
}

private fun round1(x: Double): Double = kotlin.math.round(x * 10) / 10.0
private fun pct(x: Double): String = "${round1(x * 100)} %"
