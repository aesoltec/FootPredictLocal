package com.footpredict.local.di

import com.footpredict.local.BuildConfig
import com.footpredict.local.data.aggregator.DataFusionAggregator
import com.footpredict.local.data.remote.fixture.ApiFootballFixtureDataSource
import com.footpredict.local.data.remote.fixture.FootballDataOrgFixtureDataSource
import com.footpredict.local.data.remote.fixture.FixtureRemoteDataSource
import com.footpredict.local.data.remote.fixture.SupabaseScrapedFixtureDataSource
import com.footpredict.local.data.remote.odds.OddsApiIoDataSource
import com.footpredict.local.data.remote.odds.OddsRemoteDataSource
import com.footpredict.local.data.remote.odds.SupabaseScrapedOddsDataSource
import com.footpredict.local.data.remote.stats.ApiFootballStatsDataSource
import com.footpredict.local.data.remote.stats.FootballDataOrgStandingsDataSource
import com.footpredict.local.data.remote.stats.StatsRemoteDataSource
import com.footpredict.local.data.remote.stats.SupabaseScrapedStatsDataSource
import com.footpredict.local.data.remote.stats.SupabaseScrapedXgDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        // Logs réseau uniquement en debug : en release, les headers
        // (clés API) ne doivent jamais atteindre logcat.
        if (BuildConfig.DEBUG) {
            install(Logging) { level = LogLevel.INFO }
        }
        defaultRequest {
            url("https://${BuildConfig.API_FOOTBALL_HOST}/")
        }
    }

    // ——— Fixtures ———
    @Provides
    @IntoSet
    fun provideApiFootballFixture(client: HttpClient): FixtureRemoteDataSource =
        ApiFootballFixtureDataSource(client)

    @Provides
    @IntoSet
    fun provideFootballDataOrgFixture(client: HttpClient): FixtureRemoteDataSource =
        FootballDataOrgFixtureDataSource(client)

    @Provides
    @IntoSet
    fun provideSupabaseScrapedFixture(client: HttpClient): FixtureRemoteDataSource =
        SupabaseScrapedFixtureDataSource(client)

    // ——— Stats ———
    @Provides
    @IntoSet
    fun provideApiFootballStats(client: HttpClient): StatsRemoteDataSource =
        ApiFootballStatsDataSource(client)

    @Provides
    @IntoSet
    fun provideSupabaseXg(client: HttpClient): StatsRemoteDataSource =
        SupabaseScrapedXgDataSource(client)

    @Provides
    @IntoSet
    fun provideSupabaseScrapedStats(client: HttpClient): StatsRemoteDataSource =
        SupabaseScrapedStatsDataSource(client)

    @Provides
    @IntoSet
    fun provideFootballDataOrgStandings(client: HttpClient): StatsRemoteDataSource =
        FootballDataOrgStandingsDataSource(client)

    // ——— Odds ———
    @Provides
    @IntoSet
    fun provideOddsApiIo(
        client: HttpClient,
        local: com.footpredict.local.data.local.LocalDataSource
    ): OddsRemoteDataSource =
        OddsApiIoDataSource(client, local)

    @Provides
    @IntoSet
    fun provideSupabaseOdds(client: HttpClient): OddsRemoteDataSource =
        SupabaseScrapedOddsDataSource(client)

    @Provides
    @Singleton
    fun provideDataFusionAggregator(
        fixtures: Set<@JvmSuppressWildcards FixtureRemoteDataSource>,
        stats: Set<@JvmSuppressWildcards StatsRemoteDataSource>,
        odds: Set<@JvmSuppressWildcards OddsRemoteDataSource>
    ): DataFusionAggregator = DataFusionAggregator(fixtures, stats, odds)
}
