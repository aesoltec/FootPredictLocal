package com.footpredict.local.domain.usecase

import com.footpredict.local.domain.model.MarketPrediction
import com.footpredict.local.domain.model.Odds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * UseCase pur — conversion des cotes bookmakers en probabilités implicites
 * (retrait de la marge proportionnelle).
 */
class MarketModelUseCase @Inject constructor() {

    suspend operator fun invoke(odds: Odds): MarketPrediction? = withContext(Dispatchers.Default) {
        val h = odds.homeOdds ?: return@withContext null
        val d = odds.drawOdds ?: return@withContext null
        val a = odds.awayOdds ?: return@withContext null
        if (h <= 1.0 || d <= 1.0 || a <= 1.0) return@withContext null

        val rawH = 1.0 / h
        val rawD = 1.0 / d
        val rawA = 1.0 / a
        val margin = rawH + rawD + rawA
        if (margin <= 0.0) return@withContext null

        MarketPrediction(
            matchId = odds.matchId,
            homeProb = rawH / margin,
            drawProb = rawD / margin,
            awayProb = rawA / margin
        )
    }
}
