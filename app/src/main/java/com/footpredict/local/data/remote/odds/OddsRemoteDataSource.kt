package com.footpredict.local.data.remote.odds

import com.footpredict.local.data.local.entity.OddsEntity

/**
 * Interface commune pour les cotes bookmakers.
 */
interface OddsRemoteDataSource {
    val sourceName: String
    val reliabilityScore: Double

    suspend fun fetchOdds(matchId: Int): OddsEntity?

    /**
     * Récupération groupée (1 appel réseau, indexée par matchId).
     * Vide par défaut : les sources sans support batch utilisent le un-par-un.
     */
    suspend fun fetchAllOdds(): Map<Int, OddsEntity> = emptyMap()
}
