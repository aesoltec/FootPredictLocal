package com.footpredict.local.data.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.footpredict.local.data.local.LocalDataSource
import com.footpredict.local.data.mapper.EntityMappers.toDomain
import com.footpredict.local.domain.model.EnsemblePrediction
import com.footpredict.local.domain.repository.MatchRepository
import com.footpredict.local.domain.usecase.CalculateDixonColesUseCase
import com.footpredict.local.domain.usecase.EloRatingUseCase
import com.footpredict.local.domain.usecase.EnsemblePredictionUseCase
import com.footpredict.local.domain.usecase.GenerateBettingAlertsUseCase
import com.footpredict.local.domain.usecase.MarketModelUseCase
import com.footpredict.local.data.mapper.EntityMappers.toRecentMatch
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Déclencheur de notifications push : après chaque synchro, réévalue les
 * matchs à venir et notifie ceux qui passent les filtres cumulés stricts.
 * 100 % local (Room), aucune donnée personnelle, best-effort silencieux.
 */
@HiltWorker
class PredictionTriggerWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: MatchRepository,
    private val local: LocalDataSource,
    private val dixonColes: CalculateDixonColesUseCase,
    private val marketModel: MarketModelUseCase,
    private val eloModel: EloRatingUseCase,
    private val ensemble: EnsemblePredictionUseCase,
    private val generateAlerts: GenerateBettingAlertsUseCase,
    private val notifier: AlertNotifier
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_LEAGUE = "league_id"
        const val KEY_SEASON = "season"
        const val KEY_MIN_CONF = "min_conf_pct"
        const val KEY_MIN_EDGE = "min_edge"
        const val UNIQUE_PREFIX = "prediction-trigger"
    }

    override suspend fun doWork(): Result {
        return try {
            val leagueId = inputData.getInt(KEY_LEAGUE, 39)
            val season = inputData.getInt(KEY_SEASON, 2026)
            val thresholds = TriggerFilter.Thresholds(
                minConfidencePct = inputData.getDouble(KEY_MIN_CONF, 70.0),
                minValueEdge = inputData.getDouble(KEY_MIN_EDGE, 0.15)
            )

            val resource = repository.getEnrichedMatches(leagueId, season).first {
                it is com.footpredict.local.domain.model.Resource.Success ||
                    it is com.footpredict.local.domain.model.Resource.Error
            }
            val matches = (resource as? com.footpredict.local.domain.model.Resource.Success)?.data
                ?: return Result.success()

            val nowMs = System.currentTimeMillis()
            val map = mutableMapOf<Int, EnsemblePrediction>()
            val upcoming = matches.filter {
                it.fixture.statusShort == "NS" || it.fixture.statusShort == "TBD"
            }
            for (em in upcoming) {
                if (em.homeStats == null || em.awayStats == null) continue
                val homeRecent = local.getLastFinishedForTeam(em.fixture.homeTeamId, 10)
                    .mapNotNull { it.toRecentMatch(em.fixture.homeTeamId, nowMs) }
                val awayRecent = local.getLastFinishedForTeam(em.fixture.awayTeamId, 10)
                    .mapNotNull { it.toRecentMatch(em.fixture.awayTeamId, nowMs) }
                val dc = dixonColes(
                    em.fixture, em.homeStats, em.awayStats,
                    homeRecent = homeRecent, awayRecent = awayRecent
                )
                val market = em.odds?.let { marketModel(it) }
                val elo = eloModel(em.fixture, em.eloHome ?: 1500.0, em.eloAway ?: 1500.0)
                map[em.fixture.id] = ensemble(em.fixture.id, dc, market, elo)
            }

            val passing = upcoming.filter { TriggerFilter.passes(it, map[it.fixture.id], thresholds) }
            if (passing.isEmpty()) return Result.success()

            val alerts = generateAlerts(passing, map)
            notifier.notifyHigh(alerts)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
