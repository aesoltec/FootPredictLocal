package com.footpredict.local.domain.usecase

import com.footpredict.local.domain.model.AlertSeverity
import com.footpredict.local.domain.model.AlertType
import com.footpredict.local.domain.model.DivergenceLevel
import com.footpredict.local.domain.model.EloPrediction
import com.footpredict.local.domain.model.EnrichedMatch
import com.footpredict.local.domain.model.EnsemblePrediction
import com.footpredict.local.domain.model.MarketPrediction
import com.footpredict.local.domain.model.Match
import com.footpredict.local.domain.model.MatchPrediction
import com.footpredict.local.domain.model.ValueBet
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les alertes ne doivent citer que les modèles réellement présents et
 * garder un intitulé cohérent avec leur sévérité.
 */
class GenerateBettingAlertsUseCaseTest {

    private val useCase = GenerateBettingAlertsUseCase()

    private fun match() = EnrichedMatch(
        fixture = Match(
            id = 1, leagueId = 39, season = 2026, round = null,
            dateTimestamp = 1_791_631_800L, statusShort = "NS", statusLong = null,
            homeTeamId = 1, homeTeamName = "Arsenal", homeTeamLogo = null,
            awayTeamId = 2, awayTeamName = "Sunderland", awayTeamLogo = null,
            goalsHome = null, goalsAway = null, venueName = null, venueCity = null
        ),
        homeStats = null, awayStats = null, odds = null,
        eloHome = 1600.0, eloAway = 1500.0,
        sources = listOf("understat-supabase"), conflicts = emptyList()
    )

    private fun ensemble(
        confidence: Double,
        divergence: DivergenceLevel,
        withMarket: Boolean
    ) = EnsemblePrediction(
        matchId = 1, homeWinProb = 0.55, drawProb = 0.27, awayWinProb = 0.18,
        over25Prob = 0.5, confidence = confidence, divergenceLevel = divergence,
        dixonColes = MatchPrediction(1, 0.55, 0.27, 0.18, 0.5, 0.5, 1.6, 1.1),
        market = if (withMarket) MarketPrediction(1, 0.5, 0.3, 0.2) else null,
        elo = EloPrediction(1, 0.55, 0.25, 0.2, 1600.0, 1500.0)
    )

    @Test
    fun `divergence without market never cites models`() = runTest {
        val alerts = useCase(listOf(match()), mapOf(1 to ensemble(0.4, DivergenceLevel.HIGH, false)))
        val div = alerts.single { it.type == AlertType.DIVERGENCE_WARNING }
        assertEquals("Match à risque", div.title)
        assertTrue(div.message.contains("attention"))
        for (banned in listOf("Dixon-Coles", "Marché", "Elo", "modèles", "désaccord")) {
            assertTrue("banni présent : $banned", !div.message.contains(banned))
        }
    }

    @Test
    fun `divergence wording is identical with or without market`() = runTest {
        val alerts = useCase(listOf(match()), mapOf(1 to ensemble(0.4, DivergenceLevel.HIGH, true)))
        val div = alerts.single { it.type == AlertType.DIVERGENCE_WARNING }
        assertEquals("Match à risque", div.title)
        assertTrue(div.message.contains("attention"))
    }

    @Test
    fun `high confidence tier is consistent`() = runTest {
        val alerts = useCase(listOf(match()), mapOf(1 to ensemble(0.70, DivergenceLevel.LOW, false)))
        val hc = alerts.single { it.type == AlertType.HIGH_CONFIDENCE }
        assertEquals(AlertSeverity.HIGH, hc.severity)
        assertTrue(hc.title.startsWith("Haute confiance"))
        assertTrue(hc.title.contains("Arsenal"))
    }

    @Test
    fun `moderate confidence tier is consistent`() = runTest {
        val alerts = useCase(listOf(match()), mapOf(1 to ensemble(0.59, DivergenceLevel.LOW, false)))
        val hc = alerts.single { it.type == AlertType.HIGH_CONFIDENCE }
        assertEquals(AlertSeverity.MEDIUM, hc.severity)
        assertTrue(hc.title.startsWith("Bonne confiance"))
    }

    @Test
    fun `no alert below threshold`() = runTest {
        val alerts = useCase(listOf(match()), mapOf(1 to ensemble(0.44, DivergenceLevel.MEDIUM, false)))
        assertTrue(alerts.none { it.type == AlertType.HIGH_CONFIDENCE })
        assertTrue(alerts.none { it.type == AlertType.DIVERGENCE_WARNING })
    }

    @Test
    fun `value alert uses plain french without jargon`() = runTest {
        val ens = ensemble(0.60, DivergenceLevel.LOW, true).copy(
            valueBets = listOf(ValueBet("HOME", 0.15, 0.60, 0.45))
        )
        val alerts = useCase(listOf(match()), mapOf(1 to ens))
        val vb = alerts.single { it.type == AlertType.VALUE_BET }
        assertEquals("Bonne affaire : victoire Arsenal", vb.title)
        val text = vb.title + " " + vb.message
        for (banned in listOf("edge", "Edge", "Value Bet", "HOME", "DRAW", "AWAY", "conf ", "(1)", "(X)", "(2)")) {
            assertTrue("banni présent : $banned", !text.contains(banned))
        }
    }

    @Test
    fun `alerts are capped to avoid noise`() = runTest {
        val many = (1..30).map { i ->
            match().copy(fixture = match().fixture.copy(id = i)) to
                ensemble(0.4, DivergenceLevel.HIGH, false).copy(matchId = i)
        }
        val alerts = useCase(many.map { it.first }, many.associate { it.second.matchId to it.second })
        assertTrue(alerts.size <= GenerateBettingAlertsUseCase.MAX_ALERTS)
    }
}
