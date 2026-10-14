package com.footpredict.local.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.footpredict.local.data.local.entity.EloEntity
import com.footpredict.local.data.local.entity.MatchEntity
import com.footpredict.local.data.local.entity.OddsEntity
import com.footpredict.local.data.local.entity.OddsSnapshotEntity
import com.footpredict.local.data.local.entity.SourceMappingEntity
import com.footpredict.local.data.local.entity.TeamStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchDao {
    @Query("SELECT * FROM matches WHERE leagueId = :leagueId ORDER BY dateTimestamp DESC")
    fun observeByLeague(leagueId: Int): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches WHERE id = :matchId")
    suspend fun getById(matchId: Int): MatchEntity?

    @Query("""
        SELECT * FROM matches 
        WHERE (homeTeamId = :teamId OR awayTeamId = :teamId) 
          AND statusShort = 'FT'
        ORDER BY dateTimestamp DESC 
        LIMIT :limit
    """)
    suspend fun getLastFinishedForTeam(teamId: Int, limit: Int = 10): List<MatchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(matches: List<MatchEntity>)

    @Query("DELETE FROM matches WHERE lastUpdated < :threshold")
    suspend fun deleteOlderThan(threshold: Long)

    @Query("""
        SELECT * FROM matches
        WHERE statusShort = 'FT'
          AND ((homeTeamId = :teamA AND awayTeamId = :teamB)
            OR (homeTeamId = :teamB AND awayTeamId = :teamA))
        ORDER BY dateTimestamp DESC
        LIMIT :limit
    """)
    suspend fun getHeadToHead(teamA: Int, teamB: Int, limit: Int = 10): List<MatchEntity>
}

@Dao
interface TeamStatsDao {
    @Query("""
        SELECT * FROM team_stats 
        WHERE teamId = :teamId AND leagueId = :leagueId AND season = :season
    """)
    suspend fun get(teamId: Int, leagueId: Int, season: Int): TeamStatsEntity?

    @Query("SELECT * FROM team_stats WHERE leagueId = :leagueId AND season = :season")
    fun observeByLeague(leagueId: Int, season: Int): Flow<List<TeamStatsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stats: TeamStatsEntity)

    @Query("""
        DELETE FROM team_stats
        WHERE teamId = :teamId AND leagueId = :leagueId AND season = :season
    """)
    suspend fun deleteByKey(teamId: Int, leagueId: Int, season: Int)

    /**
     * Upsert sur la clé métier (teamId, leagueId, season) : la PK auto-générée
     * seule ne détecterait jamais les doublons (REPLACE sur id = 0).
     */
    @Transaction
    suspend fun upsert(stats: TeamStatsEntity) {
        deleteByKey(stats.teamId, stats.leagueId, stats.season)
        insert(stats)
    }

    @Transaction
    suspend fun upsertAll(stats: List<TeamStatsEntity>) {
        for (s in stats) upsert(s)
    }
}

@Dao
interface OddsDao {
    @Query("SELECT * FROM odds WHERE matchId = :matchId")
    suspend fun getByMatchId(matchId: Int): OddsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(odds: OddsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(odds: List<OddsEntity>)
}

@Dao
interface SourceMappingDao {
    @Query("""
        SELECT * FROM source_mappings 
        WHERE sourceName = :source AND sourceTeamId = :sourceTeamId
    """)
    suspend fun resolve(source: String, sourceTeamId: String): SourceMappingEntity?

    @Query("SELECT * FROM source_mappings WHERE unifiedTeamId = :unifiedId")
    suspend fun getByUnifiedId(unifiedId: Int): List<SourceMappingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mapping: SourceMappingEntity)

    @Query("""
        DELETE FROM source_mappings
        WHERE sourceName = :source AND sourceTeamId = :sourceTeamId
    """)
    suspend fun deleteByKey(source: String, sourceTeamId: String)

    /** Upsert sur la clé métier (même raison que TeamStatsDao). */
    @Transaction
    suspend fun upsert(mapping: SourceMappingEntity) {
        deleteByKey(mapping.sourceName, mapping.sourceTeamId)
        insert(mapping)
    }

    @Transaction
    suspend fun upsertAll(mappings: List<SourceMappingEntity>) {
        for (m in mappings) upsert(m)
    }
}

@Dao
interface EloDao {
    @Query("SELECT * FROM elo_ratings WHERE teamId = :teamId")
    suspend fun get(teamId: Int): EloEntity?

    @Query("SELECT * FROM elo_ratings")
    fun observeAll(): Flow<List<EloEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(elo: EloEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(elos: List<EloEntity>)
}

@Dao
interface OddsSnapshotDao {
    @Query("SELECT * FROM odds_snapshots WHERE matchId = :matchId ORDER BY timestamp ASC")
    suspend fun listByMatch(matchId: Int): List<OddsSnapshotEntity>

    @Insert
    suspend fun insert(snapshot: OddsSnapshotEntity): Long

    @Query("""
        DELETE FROM odds_snapshots
        WHERE matchId = :matchId AND id NOT IN (
            SELECT id FROM odds_snapshots
            WHERE matchId = :matchId ORDER BY timestamp DESC LIMIT :keep
        )
    """)
    suspend fun prune(matchId: Int, keep: Int = 30)
}

@Dao
interface SettledPredictionDao {
    @Query("SELECT * FROM settled_predictions ORDER BY settledAt DESC")
    fun observeAll(): Flow<List<com.footpredict.local.data.local.entity.SettledPredictionEntity>>

    @Query("SELECT * FROM settled_predictions ORDER BY settledAt DESC")
    suspend fun getAll(): List<com.footpredict.local.data.local.entity.SettledPredictionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: com.footpredict.local.data.local.entity.SettledPredictionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<com.footpredict.local.data.local.entity.SettledPredictionEntity>)

    @Query("SELECT COUNT(*) FROM settled_predictions")
    suspend fun count(): Int
}
