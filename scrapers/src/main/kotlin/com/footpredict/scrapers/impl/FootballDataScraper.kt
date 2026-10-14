package com.footpredict.scrapers.impl

import com.footpredict.scrapers.BaseHttpScraper
import com.footpredict.scrapers.Feasibility
import com.footpredict.scrapers.MatchMetadata
import com.footpredict.scrapers.Outcome
import com.footpredict.scrapers.ScraperPrediction

/**
 * Football-Data.co.uk — parsing du CSV gratuit et stable.
 * Les cotes de CLÔTURE (Pinnacle/Bet365) donnent un signal Marché réel :
 * on en dérive des probabilités implicites (marge retirée au prorata),
 * dont le max devient le pronostic.
 */
class FootballDataScraper(
    divisions: List<String> = listOf("E0", "SP1", "I1", "D1", "F1"),
    seasonCode: String = "2526"
) : BaseHttpScraper() {

    override val sourceName = "football-data.co.uk"
    override val reliability = 0.88
    override val feasibility = Feasibility.SUPPORTED

    private val delegate = FootballDataUkScraper(divisions, seasonCode)

    override suspend fun fetchData(matchId: String) = delegate.fetchData(matchId)

    override suspend fun predict(metadata: MatchMetadata): ScraperPrediction? {
        val data = delegate.fetchData(metadata.toMatchId()) ?: return null
        val implied = impliedProbabilities(data.oddsHome, data.oddsDraw, data.oddsAway)
            ?: return null
        val (outcome, probability) = maxOf(
            Outcome.HOME to implied.first,
            Outcome.DRAW to implied.second,
            Outcome.AWAY to implied.third,
            compareBy { it.second }
        )
        return ScraperPrediction(
            source = sourceName,
            outcome = outcome,
            confidence = probability,
            homeOdds = data.oddsHome,
            drawOdds = data.oddsDraw,
            awayOdds = data.oddsAway,
            weight = reliability
        )
    }

    companion object {
        /**
         * Probabilités implicites, marge bookmaker retirée au prorata.
         * Null si une cote manque ou est invalide (≤ 1.0).
         */
        fun impliedProbabilities(
            home: Double?,
            draw: Double?,
            away: Double?
        ): Triple<Double, Double, Double>? {
            if (home == null || draw == null || away == null) return null
            if (home <= 1.0 || draw <= 1.0 || away <= 1.0) return null
            val rawH = 1.0 / home
            val rawD = 1.0 / draw
            val rawA = 1.0 / away
            val margin = rawH + rawD + rawA
            if (margin <= 0.0) return null
            return Triple(rawH / margin, rawD / margin, rawA / margin)
        }
    }
}
