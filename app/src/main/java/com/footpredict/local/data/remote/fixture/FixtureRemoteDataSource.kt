package com.footpredict.local.data.remote.fixture

import com.footpredict.local.data.local.entity.MatchEntity

/**
 * Interface commune pour toute source de fixtures.
 * reliabilityScore utilisé par le DataFusionAggregator.
 */
interface FixtureRemoteDataSource {
    val sourceName: String
    val reliabilityScore: Double   // 0.0 – 1.0

    suspend fun fetchFixtures(leagueId: Int, season: Int): List<MatchEntity>
}
