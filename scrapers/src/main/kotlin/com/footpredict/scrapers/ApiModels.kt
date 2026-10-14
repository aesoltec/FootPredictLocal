package com.footpredict.scrapers

/**
 * API unifiée v2 : métadonnées d’entrée, pronostic standardisé, résultat agrégé.
 * Les implémentations réseau restent dans `impl/` ; ce fichier ne fait aucun I/O.
 */

/** Entrée : ce qu’on cherche, indépendamment des IDs propres à chaque site. */
data class MatchMetadata(
    val homeTeam: String,
    val awayTeam: String,
    /** AAAA-MM-JJ. */
    val date: String
) {
    /** Clé universelle consommée par les scrapers v1. */
    fun toMatchId(): String = "$homeTeam|$awayTeam|$date"
}

/** Pronostic standardisé publié par UNE source. */
data class ScraperPrediction(
    val source: String,
    val outcome: Outcome,
    /** Probabilité 0..1 (1.0 si la source n’annonce qu’un vainqueur sec). */
    val confidence: Double,
    val homeOdds: Double? = null,
    val drawOdds: Double? = null,
    val awayOdds: Double? = null,
    val over25Prob: Double? = null,
    /** Poids du vote = fiabilité sauf exception documentée. */
    val weight: Double
)

/** Résultat final combiné par le moteur. */
data class AggregatedPrediction(
    val metadata: MatchMetadata,
    val outcome: Outcome,
    /** Part du poids total derrière le vainqueur (0..1). */
    val confidence: Double,
    /** Score de tendance : part de poids par issue (somme = 1). */
    val trendByOutcome: Map<Outcome, Double>,
    val sourcesUsed: List<String>,
    val warnings: List<String> = emptyList()
)

/**
 * Dictionnaire de synonymes d’équipes (clés normalisées minuscules).
 * Sans lui, « Leeds United » (ESPN) et « Leeds » (Understat) votent dans
 * des urnes différentes et le décompte est faux. Canoniques = formes
 * courtes stables ; étendre au fil des doublons observés (cf. backend).
 */
object TeamAliases {
    private val MAP = mapOf(
        "fc bayern munchen" to "bayern munich",
        "bayern munchen" to "bayern munich",
        "bayern" to "bayern munich",
        "1 fc koln" to "cologne",
        "fc koln" to "cologne",
        "fc cologne" to "cologne",
        "koln" to "cologne",
        "borussia monchengladbach" to "borussia m gladbach",
        "rasenballsport leipzig" to "rb leipzig",
        "sv 07 elversberg" to "elversberg",
        "hamburger sv" to "hamburg",
        "fc schalke 04" to "schalke",
        "schalke 04" to "schalke",
        "brighton hove albion" to "brighton",
        "brighton and hove albion" to "brighton",
        "west ham united" to "west ham",
        "leeds united" to "leeds",
        "newcastle united" to "newcastle",
        "manchester united" to "manchester utd",
        "wolverhampton wanderers" to "wolverhampton",
        "wolves" to "wolverhampton",
        "nottingham forest" to "nottingham",
        "nottm forest" to "nottingham",
        "tottenham hotspur" to "tottenham",
        "spurs" to "tottenham",
        "inter mailand" to "inter",
        "ac mailand" to "ac milan",
        // France : sigles et formes usuelles → nom complet stable.
        "psg" to "paris saint germain",
        "paris sg" to "paris saint germain",
        "paris saint germain" to "paris saint germain",
        "om" to "marseille",
        "olympique de marseille" to "marseille",
        "olympique marseille" to "marseille",
        "ol" to "lyon",
        "olympique lyonnais" to "lyon",
        "olympique lyon" to "lyon",
        "asm" to "monaco",
        "as monaco" to "monaco",
        "losc" to "lille",
        "lille osc" to "lille",
        "rc lens" to "lens",
        "stade rennais" to "rennes",
        "stade rennais fc" to "rennes",
        // Espagne / Italie / Allemagne : formes courtes.
        "fc barcelona" to "barcelona",
        "atletico madrid" to "atletico",
        "atletico de madrid" to "atletico",
        "athletic bilbao" to "athletic club",
        "real betis balompie" to "real betis",
        "betis" to "real betis",
        "real sociedad" to "real sociedad",
        "sociedad" to "real sociedad",
        "juve" to "juventus",
        "as roma" to "roma",
        "hellas verona" to "verona"
    )

    /** Forme canonique d’un nom d’équipe (normalisé puis aliasé). */
    fun canonical(name: String): String {
        val n = MatchKeys.normalize(name)
        return MAP[n] ?: n
    }
}
