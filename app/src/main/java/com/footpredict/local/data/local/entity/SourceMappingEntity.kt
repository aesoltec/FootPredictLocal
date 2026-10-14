package com.footpredict.local.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Table de résolution d’identités multi-sources.
 * Permet à EntityResolver de mapper source_team_id → unified_team_id.
 */
@Entity(
    tableName = "source_mappings",
    indices = [
        Index(value = ["sourceName", "sourceTeamId"], unique = true),
        Index(value = ["unifiedTeamId"])
    ]
)
data class SourceMappingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceName: String,
    val sourceTeamId: String,
    val unifiedTeamId: Int,
    val teamName: String,
    val lastUpdated: Long
)
