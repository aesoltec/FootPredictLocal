package com.footpredict.local.data.remote.stats

import com.footpredict.local.data.local.entity.TeamStatsEntity

/**
 * Interface commune pour les statistiques d’équipe (classiques + xG).
 */
interface StatsRemoteDataSource {
    val sourceName: String
    val reliabilityScore: Double

    suspend fun fetchTeamStats(
        teamId: Int,
        leagueId: Int,
        season: Int
    ): TeamStatsEntity?

    /**
     * Récupération groupée (1 appel réseau pour toute la ligue).
     * Vide par défaut : les sources sans support batch utilisent le un-par-un.
     */
    suspend fun fetchLeagueStats(leagueId: Int, season: Int): List<TeamStatsEntity> = emptyList()
}
