package com.footpredict.local.domain.repository

import com.footpredict.local.domain.model.EnrichedMatch
import com.footpredict.local.domain.model.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Interface pure du repository (couche domain).
 */
interface MatchRepository {
    fun getEnrichedMatches(leagueId: Int, season: Int): Flow<Resource<List<EnrichedMatch>>>

    /**
     * Revérifie les lives périmés (> 105 min) auprès de football-data.org
     * et bascule les terminés en Room. Retourne le nombre de bascules.
     */
    suspend fun validateLiveMatches(leagueId: Int, season: Int): Int
}
