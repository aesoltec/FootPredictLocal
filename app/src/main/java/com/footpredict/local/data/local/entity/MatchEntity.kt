package com.footpredict.local.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entité Room d’un match.
 * Contient les données de base + métadonnées de fusion multi-sources.
 */
@Entity(
    tableName = "matches",
    indices = [
        Index(value = ["leagueId"]),
        Index(value = ["homeTeamId"]),
        Index(value = ["awayTeamId"]),
        Index(value = ["dateTimestamp"]),
        Index(value = ["statusShort"]),
        Index(value = ["lastUpdated"])
    ]
)
data class MatchEntity(
    @PrimaryKey
    val id: Int,
    val externalIdsJson: String?,
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
    val sourcesJson: String?,
    val conflictsJson: String?,
    val lastUpdated: Long
)
