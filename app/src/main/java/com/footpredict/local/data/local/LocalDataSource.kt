package com.footpredict.local.data.local

import com.footpredict.local.data.local.dao.EloDao
import com.footpredict.local.data.local.dao.MatchDao
import com.footpredict.local.data.local.dao.OddsDao
import com.footpredict.local.data.local.dao.OddsSnapshotDao
import com.footpredict.local.data.local.dao.SettledPredictionDao
import com.footpredict.local.data.local.dao.SourceMappingDao
import com.footpredict.local.data.local.dao.TeamStatsDao
import com.footpredict.local.data.local.entity.EloEntity
import com.footpredict.local.data.local.entity.MatchEntity
import com.footpredict.local.data.local.entity.OddsEntity
import com.footpredict.local.data.local.entity.OddsSnapshotEntity
import com.footpredict.local.data.local.entity.SettledPredictionEntity
import com.footpredict.local.data.local.entity.SourceMappingEntity
import com.footpredict.local.data.local.entity.TeamStatsEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface LocalDataSource {
    fun observeMatchesByLeague(leagueId: Int): Flow<List<MatchEntity>>
    suspend fun getMatchById(matchId: Int): MatchEntity?
    suspend fun getHeadToHead(teamA: Int, teamB: Int, limit: Int = 10): List<MatchEntity>
    suspend fun getLastFinishedForTeam(teamId: Int, limit: Int = 10): List<MatchEntity>
    suspend fun upsertMatches(matches: List<MatchEntity>)
    suspend fun getTeamStats(teamId: Int, leagueId: Int, season: Int): TeamStatsEntity?
    suspend fun upsertTeamStats(stats: TeamStatsEntity)
    suspend fun getOdds(matchId: Int): OddsEntity?
    suspend fun upsertOdds(odds: OddsEntity)
    suspend fun recordOddsSnapshot(odds: OddsEntity)
    suspend fun getOddsHistory(matchId: Int): List<OddsSnapshotEntity>
    suspend fun getElo(teamId: Int): EloEntity?
    suspend fun upsertElo(elo: EloEntity)
    suspend fun resolveTeam(source: String, sourceTeamId: String): SourceMappingEntity?
    suspend fun upsertMapping(entity: SourceMappingEntity)
    suspend fun getAllSettled(): List<SettledPredictionEntity>
    fun observeSettled(): Flow<List<SettledPredictionEntity>>
    suspend fun upsertSettled(entity: SettledPredictionEntity)
    suspend fun upsertSettledAll(entities: List<SettledPredictionEntity>)
}

class LocalDataSourceImpl @Inject constructor(
    private val matchDao: MatchDao,
    private val teamStatsDao: TeamStatsDao,
    private val oddsDao: OddsDao,
    private val oddsSnapshotDao: OddsSnapshotDao,
    private val eloDao: EloDao,
    private val sourceMappingDao: SourceMappingDao,
    private val settledPredictionDao: SettledPredictionDao
) : LocalDataSource {
    override fun observeMatchesByLeague(leagueId: Int) = matchDao.observeByLeague(leagueId)
    override suspend fun getMatchById(matchId: Int) = matchDao.getById(matchId)
    override suspend fun getHeadToHead(teamA: Int, teamB: Int, limit: Int) =
        matchDao.getHeadToHead(teamA, teamB, limit)
    override suspend fun getLastFinishedForTeam(teamId: Int, limit: Int) =
        matchDao.getLastFinishedForTeam(teamId, limit)
    override suspend fun upsertMatches(matches: List<MatchEntity>) = matchDao.upsertAll(matches)
    override suspend fun getTeamStats(teamId: Int, leagueId: Int, season: Int) =
        teamStatsDao.get(teamId, leagueId, season)
    /**
     * Upsert avec fusion : les sources se complètent (agrégats saison d’un
     * côté, xG de l’autre). Sans fusion, chaque upsert écraserait les champs
     * de l’autre source par leurs valeurs par défaut.
     */
    override suspend fun upsertTeamStats(stats: TeamStatsEntity) {
        val old = teamStatsDao.get(stats.teamId, stats.leagueId, stats.season)
        if (old == null) {
            teamStatsDao.upsert(stats)
            return
        }
        // Les compteurs vont par bloc : si la nouvelle ligne apporte des matchs
        // joués, tous ses compteurs sont frais ; sinon on garde l’existant
        // (un vrai 0 ne doit pas écraser, ni être écrasé à tort).
        val freshCounts = stats.playedHome != 0 || stats.playedAway != 0 || stats.playedTotal != 0
        teamStatsDao.upsert(
            stats.copy(
                teamName = stats.teamName.ifBlank { old.teamName },
                teamLogo = stats.teamLogo ?: old.teamLogo,
                playedHome = if (freshCounts) stats.playedHome else old.playedHome,
                playedAway = if (freshCounts) stats.playedAway else old.playedAway,
                playedTotal = if (freshCounts) stats.playedTotal else old.playedTotal,
                goalsForHome = if (freshCounts) stats.goalsForHome else old.goalsForHome,
                goalsForAway = if (freshCounts) stats.goalsForAway else old.goalsForAway,
                goalsAgainstHome = if (freshCounts) stats.goalsAgainstHome else old.goalsAgainstHome,
                goalsAgainstAway = if (freshCounts) stats.goalsAgainstAway else old.goalsAgainstAway,
                xgForHome = stats.xgForHome ?: old.xgForHome,
                xgForAway = stats.xgForAway ?: old.xgForAway,
                xgaHome = stats.xgaHome ?: old.xgaHome,
                xgaAway = stats.xgaAway ?: old.xgaAway,
                npxgFor = stats.npxgFor ?: old.npxgFor,
                form = stats.form ?: old.form,
                lastUpdated = maxOf(stats.lastUpdated, old.lastUpdated)
            )
        )
    }
    override suspend fun getOdds(matchId: Int) = oddsDao.getByMatchId(matchId)
    override suspend fun upsertOdds(odds: OddsEntity) = oddsDao.upsert(odds)
    /**
     * Photo du mouvement de marché : uniquement si les cotes 1X2 changent
     * (évite le bruit), suivie d’un élagage aux 30 dernières.
     */
    override suspend fun recordOddsSnapshot(odds: OddsEntity) {
        val history = oddsSnapshotDao.listByMatch(odds.matchId)
        val last = history.lastOrNull()
        if (last != null &&
            last.homeOdds == odds.homeOdds &&
            last.drawOdds == odds.drawOdds &&
            last.awayOdds == odds.awayOdds
        ) return
        oddsSnapshotDao.insert(
            OddsSnapshotEntity(
                matchId = odds.matchId,
                timestamp = System.currentTimeMillis(),
                homeOdds = odds.homeOdds,
                drawOdds = odds.drawOdds,
                awayOdds = odds.awayOdds,
                source = odds.source
            )
        )
        oddsSnapshotDao.prune(odds.matchId)
    }
    override suspend fun getOddsHistory(matchId: Int) = oddsSnapshotDao.listByMatch(matchId)
    override suspend fun getElo(teamId: Int) = eloDao.get(teamId)
    override suspend fun upsertElo(elo: EloEntity) = eloDao.upsert(elo)
    override suspend fun resolveTeam(source: String, sourceTeamId: String) =
        sourceMappingDao.resolve(source, sourceTeamId)
    override suspend fun upsertMapping(entity: SourceMappingEntity) =
        sourceMappingDao.upsert(entity)
    override suspend fun getAllSettled() = settledPredictionDao.getAll()
    override fun observeSettled() = settledPredictionDao.observeAll()
    override suspend fun upsertSettled(entity: SettledPredictionEntity) =
        settledPredictionDao.upsert(entity)
    override suspend fun upsertSettledAll(entities: List<SettledPredictionEntity>) =
        settledPredictionDao.upsertAll(entities)
}
