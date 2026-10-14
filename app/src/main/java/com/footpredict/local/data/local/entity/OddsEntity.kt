package com.footpredict.local.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cotes bookmakers pour un match.
 * Source prioritaire : Odds-API.io, fallback scraping.
 */
@Entity(
    tableName = "odds",
    indices = [
        Index(value = ["matchId"], unique = true),
        Index(value = ["lastUpdated"])
    ]
)
data class OddsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val matchId: Int,
    val bookmaker: String,
    val homeOdds: Double?,
    val drawOdds: Double?,
    val awayOdds: Double?,
    val over25Odds: Double?,
    val under25Odds: Double?,
    val source: String,
    val lastUpdated: Long
)
