package com.footpredict.local.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Statistiques d’équipe (saison + fenêtres récentes).
 * Support xG (Understat) + stats classiques pour Dixon-Coles.
 */
@Entity(
    tableName = "team_stats",
    indices = [
        Index(value = ["teamId", "leagueId", "season"], unique = true),
        Index(value = ["leagueId"])
    ]
)
data class TeamStatsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val teamId: Int,
    val teamName: String,
    val teamLogo: String?,
    val leagueId: Int,
    val season: Int,
    val playedHome: Int = 0,
    val playedAway: Int = 0,
    val playedTotal: Int = 0,
    val goalsForHome: Int = 0,
    val goalsForAway: Int = 0,
    val goalsAgainstHome: Int = 0,
    val goalsAgainstAway: Int = 0,
    val xgForHome: Double? = null,
    val xgForAway: Double? = null,
    val xgaHome: Double? = null,
    val xgaAway: Double? = null,
    val npxgFor: Double? = null,
    val form: String? = null,
    val lastUpdated: Long
)
