package com.footpredict.local.data.aggregator

import com.footpredict.local.data.local.LocalDataSource
import com.footpredict.local.data.local.entity.SourceMappingEntity
import javax.inject.Inject

/**
 * Résout les identités multi-sources.
 * source_name + source_team_id → unified_team_id
 */
class EntityResolver @Inject constructor(
    private val local: LocalDataSource
) {
    suspend fun resolveTeamId(sourceName: String, sourceTeamId: String): Int? {
        return local.resolveTeam(sourceName, sourceTeamId)?.unifiedTeamId
    }

    suspend fun resolveOrRegister(
        sourceName: String,
        sourceTeamId: String,
        fallbackUnifiedId: Int,
        teamName: String
    ): Int {
        val existing = local.resolveTeam(sourceName, sourceTeamId)
        if (existing != null) return existing.unifiedTeamId
        local.upsertMapping(
            SourceMappingEntity(
                sourceName = sourceName,
                sourceTeamId = sourceTeamId,
                unifiedTeamId = fallbackUnifiedId,
                teamName = teamName,
                lastUpdated = System.currentTimeMillis()
            )
        )
        return fallbackUnifiedId
    }

    suspend fun registerMapping(
        sourceName: String,
        sourceTeamId: String,
        unifiedTeamId: Int,
        teamName: String
    ) {
        local.upsertMapping(
            SourceMappingEntity(
                sourceName = sourceName,
                sourceTeamId = sourceTeamId,
                unifiedTeamId = unifiedTeamId,
                teamName = teamName,
                lastUpdated = System.currentTimeMillis()
            )
        )
    }
}
