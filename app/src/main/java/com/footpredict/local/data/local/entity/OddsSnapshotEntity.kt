package com.footpredict.local.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Photo des cotes d’un match à un instant t (mouvement de marché).
 * Écrite uniquement quand les cotes changent (voir repository).
 */
@Entity(
    tableName = "odds_snapshots",
    indices = [
        Index(value = ["matchId"]),
        Index(value = ["timestamp"])
    ]
)
data class OddsSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val matchId: Int,
    val timestamp: Long,
    val homeOdds: Double?,
    val drawOdds: Double?,
    val awayOdds: Double?,
    val source: String
)
