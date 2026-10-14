package com.footpredict.local.data.local

import com.footpredict.local.data.local.dao.TeamStatsDao
import com.footpredict.local.data.local.entity.TeamStatsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * L’upsert intelligent complète les sources (agrégats + xG) au lieu
 * de les écraser, et ne duplique jamais une équipe.
 */
class LocalDataSourceMergeTest {

    private class FakeTeamStatsDao : TeamStatsDao {
        val rows = mutableMapOf<Triple<Int, Int, Int>, TeamStatsEntity>()
        var rowCount = 0

        override suspend fun get(teamId: Int, leagueId: Int, season: Int) =
            rows[Triple(teamId, leagueId, season)]

        override fun observeByLeague(leagueId: Int, season: Int): Flow<List<TeamStatsEntity>> =
            flowOf(rows.values.filter { it.leagueId == leagueId && it.season == season })

        override suspend fun insert(stats: TeamStatsEntity) {
            rows[Triple(stats.teamId, stats.leagueId, stats.season)] = stats
            rowCount++
        }

        override suspend fun deleteByKey(teamId: Int, leagueId: Int, season: Int) {
            rows.remove(Triple(teamId, leagueId, season))
        }

        override suspend fun upsert(stats: TeamStatsEntity) {
            deleteByKey(stats.teamId, stats.leagueId, stats.season)
            insert(stats)
        }

        override suspend fun upsertAll(stats: List<TeamStatsEntity>) {
            stats.forEach { upsert(it) }
        }
    }

    private fun stats(
        played: Int = 0, gf: Int = 0, xg: Double? = null
    ) = TeamStatsEntity(
        teamId = 1, teamName = "Arsenal", teamLogo = null,
        leagueId = 39, season = 2026,
        playedHome = played, playedAway = played, playedTotal = played * 2,
        goalsForHome = gf, goalsForAway = gf,
        goalsAgainstHome = 0, goalsAgainstAway = 0,
        xgForHome = xg, lastUpdated = 1L
    )

    private fun <T> noImpl(): T = throw NotImplementedError("fake non requis")

    private val unusedMatchDao = object : com.footpredict.local.data.local.dao.MatchDao {
        override fun observeByLeague(leagueId: Int) = flowOf(emptyList<com.footpredict.local.data.local.entity.MatchEntity>())
        override suspend fun getById(matchId: Int) = null
        override suspend fun getLastFinishedForTeam(teamId: Int, limit: Int) = emptyList<com.footpredict.local.data.local.entity.MatchEntity>()
        override suspend fun getHeadToHead(teamA: Int, teamB: Int, limit: Int) = emptyList<com.footpredict.local.data.local.entity.MatchEntity>()
        override suspend fun upsertAll(matches: List<com.footpredict.local.data.local.entity.MatchEntity>) {}
        override suspend fun deleteOlderThan(threshold: Long) {}
    }

    private val unusedOddsDao = object : com.footpredict.local.data.local.dao.OddsDao {
        override suspend fun getByMatchId(matchId: Int) = null
        override suspend fun upsert(odds: com.footpredict.local.data.local.entity.OddsEntity) {}
        override suspend fun upsertAll(odds: List<com.footpredict.local.data.local.entity.OddsEntity>) {}
    }

    private val unusedEloDao = object : com.footpredict.local.data.local.dao.EloDao {
        override suspend fun get(teamId: Int) = null
        override fun observeAll() = flowOf(emptyList<com.footpredict.local.data.local.entity.EloEntity>())
        override suspend fun upsert(elo: com.footpredict.local.data.local.entity.EloEntity) {}
        override suspend fun upsertAll(elos: List<com.footpredict.local.data.local.entity.EloEntity>) {}
    }

    private val unusedMappingDao = object : com.footpredict.local.data.local.dao.SourceMappingDao {
        override suspend fun resolve(source: String, sourceTeamId: String) = null
        override suspend fun getByUnifiedId(unifiedId: Int) = emptyList<com.footpredict.local.data.local.entity.SourceMappingEntity>()
        override suspend fun insert(mapping: com.footpredict.local.data.local.entity.SourceMappingEntity) {}
        override suspend fun deleteByKey(source: String, sourceTeamId: String) {}
        override suspend fun upsert(mapping: com.footpredict.local.data.local.entity.SourceMappingEntity) {}
        override suspend fun upsertAll(mappings: List<com.footpredict.local.data.local.entity.SourceMappingEntity>) {}
    }

    private val unusedSettledDao = object : com.footpredict.local.data.local.dao.SettledPredictionDao {
        override fun observeAll() = flowOf(emptyList<com.footpredict.local.data.local.entity.SettledPredictionEntity>())
        override suspend fun getAll() = emptyList<com.footpredict.local.data.local.entity.SettledPredictionEntity>()
        override suspend fun upsert(entity: com.footpredict.local.data.local.entity.SettledPredictionEntity) {}
        override suspend fun upsertAll(entities: List<com.footpredict.local.data.local.entity.SettledPredictionEntity>) {}
        override suspend fun count() = 0
    }

    private val unusedSnapshotDao = object : com.footpredict.local.data.local.dao.OddsSnapshotDao {
        override suspend fun listByMatch(matchId: Int) = emptyList<com.footpredict.local.data.local.entity.OddsSnapshotEntity>()
        override suspend fun insert(snapshot: com.footpredict.local.data.local.entity.OddsSnapshotEntity) = 0L
        override suspend fun prune(matchId: Int, keep: Int) {}
    }

    private fun local(dao: FakeTeamStatsDao): LocalDataSource =
        LocalDataSourceImpl(
            matchDao = unusedMatchDao,
            teamStatsDao = dao,
            oddsDao = unusedOddsDao,
            oddsSnapshotDao = unusedSnapshotDao,
            eloDao = unusedEloDao,
            sourceMappingDao = unusedMappingDao,
            settledPredictionDao = unusedSettledDao
        )

    @Test
    fun `xg row completes season aggregates without duplication`() = runTest {
        val dao = FakeTeamStatsDao()
        val ds = local(dao)
        ds.upsertTeamStats(stats(played = 5, gf = 12))
        ds.upsertTeamStats(stats(xg = 1.8))

        val merged = ds.getTeamStats(1, 39, 2026)!!
        assertEquals(5, merged.playedHome)
        assertEquals(12, merged.goalsForHome)
        assertEquals(1.8, merged.xgForHome!!, 0.001)
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `fresh counts replace stale ones including real zeros`() = runTest {
        val dao = FakeTeamStatsDao()
        val ds = local(dao)
        ds.upsertTeamStats(stats(played = 5, gf = 12))
        ds.upsertTeamStats(stats(played = 6, gf = 0))
        val merged = ds.getTeamStats(1, 39, 2026)!!
        assertEquals(6, merged.playedHome)
        assertEquals(0, merged.goalsForHome)
    }
}
