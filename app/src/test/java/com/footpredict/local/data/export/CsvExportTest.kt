package com.footpredict.local.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExportTest {

    private fun row() = HistoryRow(
        matchId = "123",
        matchDate = "2026-10-17T11:30:00Z",
        competitionName = "Premier League",
        homeTeam = "Arsenal, FC",
        awayTeam = "Chelsea",
        finalScoreHome = 2,
        finalScoreAway = 1,
        appMarketProposal = "Victoire Arsenal",
        appConfidencePct = 59.0,
        bookmakerOdds = 2.1,
        bookmakerImpliedPct = 47.62,
        valueBetEdgePct = 11.38,
        totalXgHome = 1.85,
        totalXgAway = 0.56,
        predictionStatus = HistoryRow.STATUS_WON,
        predictionErrorMargin = 0.41
    )

    @Test
    fun `header order matches spec exactly`() {
        val csv = historyToCsv(emptyList())
        val header = csv.trim()
        assertEquals(
            "match_id,match_date,competition_name,home_team,away_team," +
                "final_score_home,final_score_away," +
                "app_market_proposal,app_confidence_pct," +
                "bookmaker_odds,bookmaker_implied_pct," +
                "value_bet_edge_pct," +
                "total_xg_home,total_xg_away," +
                "prediction_status,prediction_error_margin," +
                "simulation_gain_10e",
            header
        )
    }

    @Test
    fun `row escapes commas and formats doubles`() {
        val csv = historyToCsv(listOf(row()))
        val line = csv.trim().lines()[1]
        assertTrue(line.contains("\"Arsenal, FC\""))
        assertTrue(line.contains("59.00"))
        assertTrue(line.contains("GAGNÉ"))
        assertEquals(17, line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex()).size)
    }

    @Test
    fun `simulation column precomputes ten euro ledger`() {
        // GAGNÉ @2.10 → +11.00 ; PERDU → −10.00 ; EN_ATTENTE → vide.
        val won = historyToCsv(listOf(row().copy(simulationGain10e = 11.0)))
        assertTrue(won.trim().lines()[1].endsWith(",11.00"))
        val lost = historyToCsv(
            listOf(row().copy(predictionStatus = HistoryRow.STATUS_LOST, simulationGain10e = -10.0))
        )
        assertTrue(lost.trim().lines()[1].endsWith(",-10.00"))
        val pending = historyToCsv(
            listOf(row().copy(predictionStatus = HistoryRow.STATUS_PENDING, simulationGain10e = null))
        )
        assertTrue(pending.trim().lines()[1].endsWith(","))
    }

    @Test
    fun `nulls render empty`() {
        val csv = historyToCsv(listOf(row().copy(finalScoreHome = null, bookmakerOdds = null)))
        val line = csv.trim().lines()[1]
        assertTrue(line.contains("EN_ATTENTE") || line.contains("GAGNÉ"))
        assertTrue(!line.contains("null"))
    }
}
