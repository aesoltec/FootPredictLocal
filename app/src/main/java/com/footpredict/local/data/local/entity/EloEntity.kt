package com.footpredict.local.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Rating Elo d’une équipe.
 * Mis à jour après chaque match terminé.
 */
@Entity(
    tableName = "elo_ratings",
    indices = [Index(value = ["teamId"], unique = true)]
)
data class EloEntity(
    @PrimaryKey
    val teamId: Int,
    val rating: Double,
    val matchesPlayed: Int = 0,
    val lastUpdated: Long
)
