package com.footpredict.local.di

import android.content.Context
import androidx.room.Room
import com.footpredict.local.data.local.FootPredictDatabase
import com.footpredict.local.data.local.LocalDataSource
import com.footpredict.local.data.local.LocalDataSourceImpl
import com.footpredict.local.data.local.dao.EloDao
import com.footpredict.local.data.local.dao.MatchDao
import com.footpredict.local.data.local.dao.OddsDao
import com.footpredict.local.data.local.dao.OddsSnapshotDao
import com.footpredict.local.data.local.dao.SourceMappingDao
import com.footpredict.local.data.local.dao.SettledPredictionDao
import com.footpredict.local.data.local.dao.TeamStatsDao
import com.footpredict.local.data.repository.MatchRepositoryImpl
import com.footpredict.local.domain.repository.MatchRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FootPredictDatabase =
        Room.databaseBuilder(context, FootPredictDatabase::class.java, "footpredict.db")
            .addMigrations(FootPredictDatabase.MIGRATION_4_5)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideMatchDao(db: FootPredictDatabase): MatchDao = db.matchDao()
    @Provides fun provideOddsSnapshotDao(db: FootPredictDatabase): OddsSnapshotDao = db.oddsSnapshotDao()
    @Provides fun provideTeamStatsDao(db: FootPredictDatabase): TeamStatsDao = db.teamStatsDao()
    @Provides fun provideOddsDao(db: FootPredictDatabase): OddsDao = db.oddsDao()
    @Provides fun provideEloDao(db: FootPredictDatabase): EloDao = db.eloDao()
    @Provides fun provideSettledPredictionDao(db: FootPredictDatabase): SettledPredictionDao = db.settledPredictionDao()
    @Provides fun provideSourceMappingDao(db: FootPredictDatabase): SourceMappingDao = db.sourceMappingDao()

    @Provides
    @Singleton
    fun provideLocalDataSource(
        matchDao: MatchDao,
        teamStatsDao: TeamStatsDao,
        oddsDao: OddsDao,
        oddsSnapshotDao: OddsSnapshotDao,
        eloDao: EloDao,
        sourceMappingDao: SourceMappingDao,
        settledPredictionDao: SettledPredictionDao
    ): LocalDataSource = LocalDataSourceImpl(matchDao, teamStatsDao, oddsDao, oddsSnapshotDao, eloDao, sourceMappingDao, settledPredictionDao)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindMatchRepository(impl: MatchRepositoryImpl): MatchRepository
}
