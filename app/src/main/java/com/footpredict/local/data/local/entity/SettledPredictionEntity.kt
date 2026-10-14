package com.footpredict.local.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Historique des prédictions settled (pour stats de performance).
 */
@Entity(
    tableName = "settled_predictions",
    indices = [
        Index(value = ["matchId"], unique = true),
        Index(value = ["settledAt"])
    ]
)
data class SettledPredictionEntity(
    @PrimaryKey
    val matchId: Int,
    val predictedOutcome: String,
    val actualOutcome: String,
    val homeWinProb: Double,
    val drawProb: Double,
    val awayWinProb: Double,
    val confidence: Double,
    val wasHit: Boolean,
    val edgeOnPredicted: Double?,
    val settledAt: Long,
    val over25Prob: Double? = null,
    val bttsYesProb: Double? = null,
    val predictedMarket: String? = null,
    val bookmakerOdds: Double? = null
)
