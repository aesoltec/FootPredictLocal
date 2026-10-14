package com.footpredict.local.domain.usecase

import com.footpredict.local.domain.model.EnrichedMatch
import com.footpredict.local.domain.model.Match
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidateLiveMatchesUseCaseTest {

    private val useCase = ValidateLiveMatchesUseCase()

    private fun match(status: String, kickoffAgoMin: Long, nowMs: Long) = EnrichedMatch(
        fixture = Match(
            id = 1, leagueId = 39, season = 2026, round = null,
            dateTimestamp = (nowMs - kickoffAgoMin * 60_000L) / 1000,
            statusShort = status, statusLong = null,
            homeTeamId = 1, homeTeamName = "A", homeTeamLogo = null,
            awayTeamId = 2, awayTeamName = "B", awayTeamLogo = null,
            goalsHome = 1, goalsAway = 0, venueName = null, venueCity = null
        ),
        homeStats = null, awayStats = null, odds = null,
        eloHome = null, eloAway = null, sources = emptyList(), conflicts = emptyList()
    )

    @Test
    fun `live beyond 105 minutes needs recheck`() {
        val now = System.currentTimeMillis()
        assertTrue(useCase.needsRecheck(match("2H", 120, now), now))
        assertTrue(useCase.needsRecheck(match("HT", 200, now), now))
    }

    @Test
    fun `recent live or finished never rechecked`() {
        val now = System.currentTimeMillis()
        assertFalse(useCase.needsRecheck(match("1H", 20, now), now))
        assertFalse(useCase.needsRecheck(match("FT", 500, now), now))
        assertFalse(useCase.needsRecheck(match("NS", 0, now), now))
    }

    @Test
    fun `overdue prematch needs recheck after 105 minutes`() {
        // Purge EN_ATTENTE (recalibrage 2026-10-13) : NS + coup d’envoi
        // dépassé de 106 min → revérifié ; futur (kickoff dans 60 min) → non.
        val now = System.currentTimeMillis()
        assertTrue(useCase.needsPrematchRecheck(match("NS", 106, now), now))
        assertTrue(useCase.needsPrematchRecheck(match("TBD", 300, now), now))
        assertFalse(useCase.needsPrematchRecheck(match("NS", -60, now), now))
        assertFalse(useCase.needsPrematchRecheck(match("FT", 500, now), now))
        assertFalse(useCase.needsPrematchRecheck(match("1H", 200, now), now))
    }

    @Test
    fun `watch covers live and near kickoff only`() {
        // Synchro live : en cours toujours ; prematch −15 min (imminent) et
        // +60 min (non confirmé) oui ; −30 min et +200 min non ; FT jamais.
        assertTrue(ValidateLiveMatchesUseCase.warrantsWatch("1H", 30))
        assertTrue(ValidateLiveMatchesUseCase.warrantsWatch("HT", 50))
        assertTrue(ValidateLiveMatchesUseCase.warrantsWatch("NS", -10))
        assertTrue(ValidateLiveMatchesUseCase.warrantsWatch("NS", 60))
        assertFalse(ValidateLiveMatchesUseCase.warrantsWatch("NS", -30))
        assertFalse(ValidateLiveMatchesUseCase.warrantsWatch("NS", 200))
        assertFalse(ValidateLiveMatchesUseCase.warrantsWatch("FT", 500))
        assertFalse(ValidateLiveMatchesUseCase.warrantsWatch("CANC", 500))
    }

    @Test
    fun `live update applies on confirmed change only`() {
        val u = ValidateLiveMatchesUseCase::shouldApplyLiveUpdate
        // Bascule rapide fin de match (fini l’attente 105 min).
        assertTrue(u("1H", 1, 0, "FT", 2, 1))
        assertTrue(u("NS", null, null, "FT", 1, 1))
        // Coup d’envoi : statut seul suffit (buts gratuits souvent nulls).
        assertTrue(u("NS", null, null, "LIVE", null, null))
        // Score en cours qui bouge.
        assertTrue(u("1H", 1, 0, "1H", 2, 0))
        // Mi-temps confirmée.
        assertTrue(u("LIVE", 1, 1, "HT", 1, 1))
        // Identique → non (anti-boucles) ; buts nulls → on n’efface rien.
        assertFalse(u("1H", 1, 0, "1H", 1, 0))
        assertFalse(u("1H", 1, 0, "1H", null, null))
        // FT en cache intouchable ; futur NS ignoré.
        assertFalse(u("FT", 2, 1, "FT", 2, 1))
        assertFalse(u("FT", 2, 1, "CANC", null, null))
        assertFalse(u("NS", null, null, "NS", null, null))
    }
}
