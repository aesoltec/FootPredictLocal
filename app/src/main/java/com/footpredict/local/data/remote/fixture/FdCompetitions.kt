package com.footpredict.local.data.remote.fixture

/**
 * Référentiel partagé football-data.org : IDs de ligues de l’app
 * (référentiel API-Football) → codes de compétitions FD.
 * Plan gratuit : PL, PD, SA, BL1, FL1, DED, PPL, ELC, BSA, CL, WC, EC.
 */
object FdCompetitions {
    private val CODES = mapOf(
        39 to "PL",    // Premier League
        140 to "PD",   // La Liga
        135 to "SA",   // Serie A
        78 to "BL1",   // Bundesliga
        61 to "FL1",   // Ligue 1
        88 to "DED",   // Eredivisie
        94 to "PPL",   // Primeira Liga
        41 to "ELC",   // Championship
        2 to "CL",     // Champions League
        4 to "EC",     // Euro (plan gratuit)
        71 to "BSA",   // Brasileirão (plan gratuit)
        1 to "WC"      // Coupe du monde (plan gratuit)
    )

    fun codeFor(leagueId: Int): String? = CODES[leagueId]
}

/**
 * Garde-fou partagé football-data.org : le fournisseur bannit les abus du
 * rateLimiter (10 req/min). Si l’API signale ≤ 1 requête restante, on lève
 * pour reporter à la prochaine synchro (dégradation gracieuse).
 */
object FdThrottle {
    fun check(availableMinute: Int?) {
        if (availableMinute != null && availableMinute <= 1) {
            throw QuotaExhaustedException(availableMinute)
        }
    }
}

class QuotaExhaustedException(val available: Int) :
    Exception("quota football-data.org épuisé (disponible=$available)")
