package com.footpredict.local.domain.model

/** Modèle métier d’un match. */
data class Match(
    val id: Int,
    val leagueId: Int,
    val season: Int,
    val round: String?,
    val dateTimestamp: Long,
    val statusShort: String,
    val statusLong: String?,
    val homeTeamId: Int,
    val homeTeamName: String,
    val homeTeamLogo: String?,
    val awayTeamId: Int,
    val awayTeamName: String,
    val awayTeamLogo: String?,
    val goalsHome: Int?,
    val goalsAway: Int?,
    val xgHome: Double? = null,
    val xgAway: Double? = null,
    val venueName: String?,
    val venueCity: String?,
    val sources: List<String> = emptyList(),
    val conflicts: List<String> = emptyList()
)

/**
 * Ligne de forme récente d’une équipe (un match terminé).
 * @param goalsFor buts marqués par l’équipe suivie.
 * @param goalsAgainst buts encaissés par l’équipe suivie.
 * @param daysAgo ancienneté du match en jours (pondération time decay).
 * @param wasHome true si l’équipe jouait à domicile.
 */
data class RecentMatch(
    val goalsFor: Int,
    val goalsAgainst: Int,
    val daysAgo: Double,
    val wasHome: Boolean
)

/** Statistiques d’équipe (saison + xG). */
data class TeamStats(
    val teamId: Int,
    val teamName: String,
    val teamLogo: String?,
    val leagueId: Int,
    val season: Int,
    val playedHome: Int,
    val playedAway: Int,
    val playedTotal: Int,
    val goalsForHome: Int,
    val goalsForAway: Int,
    val goalsAgainstHome: Int,
    val goalsAgainstAway: Int,
    val xgForHome: Double? = null,
    val xgForAway: Double? = null,
    val xgaHome: Double? = null,
    val xgaAway: Double? = null,
    val npxgFor: Double? = null,
    val form: String? = null
)

/** Cotes bookmakers. */
data class Odds(
    val matchId: Int,
    val bookmaker: String,
    val homeOdds: Double?,
    val drawOdds: Double?,
    val awayOdds: Double?,
    val over25Odds: Double? = null,
    val under25Odds: Double? = null,
    val source: String
)

/** Match enrichi multi-sources. */
data class EnrichedMatch(
    val fixture: Match,
    val homeStats: TeamStats?,
    val awayStats: TeamStats?,
    val odds: Odds?,
    val eloHome: Double?,
    val eloAway: Double?,
    val sources: List<String>,
    val conflicts: List<String> = emptyList()
)

/** Prédiction Dixon-Coles. */
data class MatchPrediction(
    val matchId: Int,
    val homeWinProb: Double,
    val drawProb: Double,
    val awayWinProb: Double,
    val over25Prob: Double,
    val under25Prob: Double,
    val lambdaHome: Double,
    val lambdaAway: Double,
    val bttsYesProb: Double = 0.5,
    val bttsNoProb: Double = 0.5,
    /**
     * Lignes sécurisées v1.3.2 (Safe du jour) : toujours calculées par le
     * moteur réel ; les défauts ne servent qu’aux instances de test
     * construites à la main (jamais en production).
     */
    val over15Prob: Double = 0.75,
    val over35Prob: Double = 0.35
)

/** Prédiction marché (cotes implicites). */
data class MarketPrediction(
    val matchId: Int,
    val homeProb: Double,
    val drawProb: Double,
    val awayProb: Double
)

/** Prédiction Elo. */
data class EloPrediction(
    val matchId: Int,
    val homeProb: Double,
    val drawProb: Double,
    val awayProb: Double,
    val eloHome: Double,
    val eloAway: Double
)

enum class DivergenceLevel { LOW, MEDIUM, HIGH }

/** Value bet détecté. */
data class ValueBet(
    val outcome: String,          // "HOME" | "DRAW" | "AWAY"
    val edge: Double,             // P_ensemble - P_implicite
    val ensembleProb: Double,
    val impliedProb: Double
)

/** Prédiction d’ensemble finale. */
data class EnsemblePrediction(
    val matchId: Int,
    val homeWinProb: Double,
    val drawProb: Double,
    val awayWinProb: Double,
    val over25Prob: Double?,
    val confidence: Double,           // 0.0 – 0.99
    val divergenceLevel: DivergenceLevel,
    val dixonColes: MatchPrediction?,
    val market: MarketPrediction?,
    val elo: EloPrediction?,
    val valueBets: List<ValueBet> = emptyList(),
    val bttsYesProb: Double? = null,
    /** Lignes sécurisées v1.3.2 (retransmises du Dixon-Coles, null si absentes). */
    val over15Prob: Double? = null,
    val over35Prob: Double? = null
)
