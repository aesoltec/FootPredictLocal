package com.footpredict.local.domain.model

/**
 * Types d’alertes betting générées par le moteur.
 */
enum class AlertType {
    VALUE_BET,          // edge ≥ 10 % vs marché
    DROPPING_ODDS,      // baisse significative des cotes (steam)
    MONEYWAY,           // volume Betfair déséquilibré
    HIGH_CONFIDENCE,    // ensemble confidence élevée + faible divergence
    DIVERGENCE_WARNING  // modèles fortement en désaccord
}

enum class AlertSeverity { LOW, MEDIUM, HIGH }

/**
 * Alerte betting affichable dans l’UI / notifications.
 */
data class BettingAlert(
    val id: String,
    val matchId: Int,
    val homeTeam: String,
    val awayTeam: String,
    val type: AlertType,
    val severity: AlertSeverity,
    val title: String,
    val message: String,
    val edge: Double? = null,           // pour VALUE_BET
    val confidence: Double? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Résultat d’une prédiction une fois le match terminé.
 *
 * Sémantique v1.3.1 (audit externe 2026-10-14) : [wasHit] valide le MARCHÉ
 * PROPOSÉ ([predictedMarket], même fonction que l’export CSV), pas le 1X2
 * seul. [predictedOutcome] 1X2 reste renseigné pour les stats « Par résultat ».
 * [bookmakerOdds] fige la cote du NATO au moment du soldé (lignes anciennes :
 * null → repli sur la cote courante, vide honnête si absente).
 * Lignes soldées IMMUABLES : le pipeline ne recalcule jamais un match déjà
 * en base (le flip GAGNÉ→PERDU du match 560574 entre deux exports est interdit).
 */
data class SettledPrediction(
    val matchId: Int,
    val predictedOutcome: String,       // "HOME" | "DRAW" | "AWAY"
    val actualOutcome: String,
    val homeWinProb: Double,
    val drawProb: Double,
    val awayWinProb: Double,
    val confidence: Double,
    val wasHit: Boolean,
    val edgeOnPredicted: Double? = null,
    val settledAt: Long,
    val over25Prob: Double? = null,
    val bttsYesProb: Double? = null,
    val predictedMarket: String? = null,
    val bookmakerOdds: Double? = null
)

/**
 * Stats de performance agrégées du modèle.
 */
data class PerformanceStats(
    val totalPredictions: Int = 0,
    val hits: Int = 0,
    val hitRate: Double = 0.0,          // 0–1
    val avgConfidence: Double = 0.0,
    val highConfidenceHitRate: Double = 0.0,  // conf ≥ 0.55
    val valueBetCount: Int = 0,
    val valueBetHits: Int = 0,
    val valueBetHitRate: Double = 0.0,
    val brierScore: Double = 0.0,       // plus bas = meilleur
    val byOutcome: Map<String, OutcomeStats> = emptyMap(),
    val lastUpdated: Long = 0L
)

data class OutcomeStats(
    val predicted: Int = 0,
    val hits: Int = 0,
    val hitRate: Double = 0.0
)
