package com.footpredict.local.domain.usecase

import com.footpredict.local.domain.model.EnrichedMatch
import javax.inject.Inject

/**
 * Validateur de matchs liveDatas : un match « en cours » dont le coup de
 * sifflet initial remonte à plus de [STALE_AFTER_MINUTES] (90 + pause +
 * arrêts de jeu) est suspect — son statut doit être revérifié auprès d’une
 * source officielle (football-data.org, PAS FotMob/SofaScore : endpoints
 * non contractuels et protégés, impropres à une vérification fiable).
 * Pur et testé ; le repository applique la bascule Room.
 */
class ValidateLiveMatchesUseCase @Inject constructor() {

    companion object {
        const val STALE_AFTER_MINUTES = 105L
        val LIVE_STATUSES = setOf("1H", "HT", "2H", "ET", "BT", "P", "LIVE", "INT")
        /** Statuts prematch : un NS/TBD/PST dont le coup d’envoi + 105 min est
         *  passé est suspect (purge EN_ATTENTE, recalibrage 2026-10-13). */
        val PREMATCH_STATUSES = setOf("NS", "TBD", "PST")
        /** Fenêtre de guet avant coup d’envoi (démarrage imminent). */
        const val WATCH_BEFORE_MINUTES = 15L

        /**
         * Vrai si le match mérite le guet live (polling 30 s) : en cours, ou
         * coup d’envoi dans ≤ 15 min / passé depuis ≤ 105 min sans confirmation.
         * Au-delà (reporté non confirmé), la synchro 6 h reprend le relais
         * (pas de polling infini → batterie préservée). Pur et testé.
         */
        fun warrantsWatch(statusShort: String, elapsedMin: Long): Boolean =
            statusShort in LIVE_STATUSES ||
                (statusShort in PREMATCH_STATUSES &&
                    elapsedMin in -WATCH_BEFORE_MINUTES..STALE_AFTER_MINUTES)

        /**
         * Décision pure d’application d’une mise à jour live (jamais de score
         * inventé) :
         * - FT/CANC confirmé + cache non FT → bascule RAPIDE (fini l’attente
         *   105 min pour les fins de match).
         * - Statut live confirmé + statut ou buts confirmés différents → sync
         *   (scores en cours + bascule NS→live au coup d’envoi).
         * - Buts confirmés nulls → jamais écrasés (le plan gratuit FD ne les
         *   renseigne qu’en fin de match) ; le statut seul peut basculer.
         * - Cache FT → jamais touché (pipeline settled propriétaire).
         * - Mises à jour sans changement → false (anti-boucles de rechargement).
         */
        fun shouldApplyLiveUpdate(
            cachedStatus: String,
            cachedGh: Int?,
            cachedGa: Int?,
            fdStatus: String,
            fdGh: Int?,
            fdGa: Int?
        ): Boolean {
            if (cachedStatus == "FT") return false
            if (fdStatus == "FT" || fdStatus == "CANC") return true
            if (fdStatus !in LIVE_STATUSES) return false
            if (cachedStatus !in LIVE_STATUSES && cachedStatus !in PREMATCH_STATUSES) return false
            if (fdStatus != cachedStatus) return true
            if (fdGh != null && fdGa != null && (fdGh != cachedGh || fdGa != cachedGa)) return true
            return false
        }
    }

    /** Vrai si le live doit être revérifié (présumé terminé côté source). */
    fun needsRecheck(match: EnrichedMatch, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (match.fixture.statusShort !in LIVE_STATUSES) return false
        val elapsedMin = (nowMs - match.fixture.dateTimestamp * 1000) / 60_000L
        return elapsedMin > STALE_AFTER_MINUTES
    }

    /**
     * Vrai si un prematch doit être revérifié : coup d’envoi dépassé de plus
     * de [STALE_AFTER_MINUTES] (ex. IDs 560603–560621 EN_ATTENTE). Le
     * repository interroge alors football-data.org et ne bascule qu’avec un
     * score FT confirmé (jamais de score inventé).
     */
    fun needsPrematchRecheck(match: EnrichedMatch, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (match.fixture.statusShort !in PREMATCH_STATUSES) return false
        val elapsedMin = (nowMs - match.fixture.dateTimestamp * 1000) / 60_000L
        return elapsedMin > STALE_AFTER_MINUTES
    }

    /** Filtre les lives périmés d’une liste. */
    fun staleLiveMatches(
        matches: List<EnrichedMatch>,
        nowMs: Long = System.currentTimeMillis()
    ): List<EnrichedMatch> = matches.filter { needsRecheck(it, nowMs) }
}
