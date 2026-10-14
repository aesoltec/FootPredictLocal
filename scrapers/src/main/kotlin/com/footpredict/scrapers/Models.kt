package com.footpredict.scrapers

/** Issue possible d’un match : 1 = domicile, X = nul, 2 = extérieur. */
enum class Outcome { HOME, DRAW, AWAY }

/** Statistiques standardisées d’une équipe pour un match ou une période. */
data class TeamStats(
    val played: Int = 0,
    val won: Int = 0,
    val drawn: Int = 0,
    val lost: Int = 0,
    val goalsFor: Int = 0,
    val goalsAgainst: Int = 0,
    val expectedGoalsFor: Double? = null,
    val expectedGoalsAgainst: Double? = null,
    /** Forme récente : 'W' / 'D' / 'L', du plus ancien au plus récent. */
    val form: List<Char> = emptyList()
)

/**
 * Données brutes standardisées d’un match, quelle que soit la source.
 * Tous les champs sauf les noms sont nullables : chaque site ne fournit
 * qu’un sous-ensemble (documenté dans chaque scraper).
 */
data class MatchData(
    val homeTeam: String,
    val awayTeam: String,
    /** Epoch secondes (UTC), 0 si inconnu. */
    val dateTimestamp: Long = 0L,
    /** NS = à venir, FT = terminé, LIVE, POSTP… */
    val status: String = "NS",
    val scoreHome: Int? = null,
    val scoreAway: Int? = null,
    val statsHome: TeamStats? = null,
    val statsAway: TeamStats? = null,
    val xgHome: Double? = null,
    val xgAway: Double? = null,
    val oddsHome: Double? = null,
    val oddsDraw: Double? = null,
    val oddsAway: Double? = null,
    val source: String
)

/** Pronostic explicite publié par une source (rare : la plupart ne donnent que des stats). */
data class Prediction(
    val outcome: Outcome,
    /** Probabilité 0..1 estimée par la source (1.0 si simple vainqueur annoncé). */
    val probability: Double,
    val over25Prob: Double? = null,
    val bttsYesProb: Double? = null,
    val source: String,
    /** Poids du vote = fiabilité de la source sauf exception documentée. */
    val weight: Double
)

/** Résultat unitaire d’un scraper : succès partiel accepté, échec isolé. */
data class ScrapeResult(
    val source: String,
    val reliability: Double,
    val data: MatchData? = null,
    val prediction: Prediction? = null,
    val error: String? = null
) {
    val succeeded: Boolean get() = error == null
}
