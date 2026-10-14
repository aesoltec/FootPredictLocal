package com.footpredict.local.data.mapper

import com.footpredict.local.data.local.entity.EloEntity
import com.footpredict.local.data.local.entity.MatchEntity
import com.footpredict.local.data.local.entity.OddsEntity
import com.footpredict.local.data.local.entity.SettledPredictionEntity
import com.footpredict.local.data.local.entity.TeamStatsEntity
import com.footpredict.local.domain.model.Match
import com.footpredict.local.domain.model.Odds
import com.footpredict.local.domain.model.RecentMatch
import com.footpredict.local.domain.model.SettledPrediction
import com.footpredict.local.domain.model.TeamStats

/**
 * Mappers Entity ↔ Domain (couche data uniquement).
 * Isolés pour éviter de polluer les UseCases / UI.
 */
object EntityMappers {

    fun MatchEntity.toDomain(): Match = Match(
        id = id,
        leagueId = leagueId,
        season = season,
        round = round,
        dateTimestamp = dateTimestamp,
        statusShort = statusShort,
        statusLong = statusLong,
        homeTeamId = homeTeamId,
        homeTeamName = homeTeamName,
        homeTeamLogo = homeTeamLogo,
        awayTeamId = awayTeamId,
        awayTeamName = awayTeamName,
        awayTeamLogo = awayTeamLogo,
        goalsHome = goalsHome,
        goalsAway = goalsAway,
        xgHome = xgHome,
        xgAway = xgAway,
        venueName = venueName,
        venueCity = venueCity,
        sources = sourcesJson?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
        conflicts = conflictsJson?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    )

    fun TeamStatsEntity.toDomain(): TeamStats = TeamStats(
        teamId = teamId,
        teamName = teamName,
        teamLogo = teamLogo,
        leagueId = leagueId,
        season = season,
        playedHome = playedHome,
        playedAway = playedAway,
        playedTotal = playedTotal,
        goalsForHome = goalsForHome,
        goalsForAway = goalsForAway,
        goalsAgainstHome = goalsAgainstHome,
        goalsAgainstAway = goalsAgainstAway,
        xgForHome = xgForHome,
        xgForAway = xgForAway,
        xgaHome = xgaHome,
        xgaAway = xgaAway,
        npxgFor = npxgFor,
        form = form
    )

    fun OddsEntity.toDomain(): Odds = Odds(
        matchId = matchId,
        bookmaker = bookmaker,
        homeOdds = homeOdds,
        drawOdds = drawOdds,
        awayOdds = awayOdds,
        over25Odds = over25Odds,
        under25Odds = under25Odds,
        source = source
    )

    fun SettledPredictionEntity.toDomain(): SettledPrediction = SettledPrediction(
        matchId = matchId,
        predictedOutcome = predictedOutcome,
        actualOutcome = actualOutcome,
        homeWinProb = homeWinProb,
        drawProb = drawProb,
        awayWinProb = awayWinProb,
        confidence = confidence,
        wasHit = wasHit,
        edgeOnPredicted = edgeOnPredicted,
        settledAt = settledAt,
        over25Prob = over25Prob,
        bttsYesProb = bttsYesProb,
        predictedMarket = predictedMarket,
        bookmakerOdds = bookmakerOdds
    )

    fun SettledPrediction.toEntity(): SettledPredictionEntity = SettledPredictionEntity(
        matchId = matchId,
        predictedOutcome = predictedOutcome,
        actualOutcome = actualOutcome,
        homeWinProb = homeWinProb,
        drawProb = drawProb,
        awayWinProb = awayWinProb,
        confidence = confidence,
        wasHit = wasHit,
        edgeOnPredicted = edgeOnPredicted,
        settledAt = settledAt,
        over25Prob = over25Prob,
        bttsYesProb = bttsYesProb,
        predictedMarket = predictedMarket,
        bookmakerOdds = bookmakerOdds
    )

    fun EloEntity.ratingOrDefault(default: Double = 1500.0): Double = rating.takeIf { it > 0 } ?: default

    /**
     * Ligne de forme récente vue d’une équipe : buts orientés (marqués /
     * encaissés par [teamId]) + ancienneté en jours pour le time decay.
     */
    fun MatchEntity.toRecentMatch(teamId: Int, nowMs: Long = System.currentTimeMillis()): RecentMatch? {
        val gh = goalsHome ?: return null
        val ga = goalsAway ?: return null
        val wasHome = homeTeamId == teamId
        return RecentMatch(
            goalsFor = if (wasHome) gh else ga,
            goalsAgainst = if (wasHome) ga else gh,
            daysAgo = ((nowMs - dateTimestamp * 1000).toDouble() / 86_400_000.0).coerceAtLeast(0.0),
            wasHome = wasHome
        )
    }
}
