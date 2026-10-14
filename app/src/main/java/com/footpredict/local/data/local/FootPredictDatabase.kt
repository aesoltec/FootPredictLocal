package com.footpredict.local.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

@Database(
    entities = [
        MatchEntity::class,
        TeamStatsEntity::class,
        OddsEntity::class,
        SourceMappingEntity::class,
        EloEntity::class,
        SettledPredictionEntity::class,
        OddsSnapshotEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class FootPredictDatabase : RoomDatabase() {
    abstract fun matchDao(): MatchDao
    abstract fun teamStatsDao(): TeamStatsDao
    abstract fun oddsDao(): OddsDao
    abstract fun sourceMappingDao(): SourceMappingDao
    abstract fun eloDao(): EloDao
    abstract fun settledPredictionDao(): SettledPredictionDao
    abstract fun oddsSnapshotDao(): OddsSnapshotDao

    companion object {
        /**
         * Migration 4→5 (audit externe 2026-10-14) : fige le marché proposé +
         * la cote au moment du soldé. Colonnes NULLABLES → historique existant
         * préservé (pas de wipe destructif : l’historique des paris est sacré).
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settled_predictions ADD COLUMN predictedMarket TEXT")
                db.execSQL("ALTER TABLE settled_predictions ADD COLUMN bookmakerOdds REAL")
            }
        }
    }
}
