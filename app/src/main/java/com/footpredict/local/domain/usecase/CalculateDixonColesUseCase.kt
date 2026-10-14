package com.footpredict.local.domain.usecase

import com.footpredict.local.domain.model.Match
import com.footpredict.local.domain.model.MatchPrediction
import com.footpredict.local.domain.model.RecentMatch
import com.footpredict.local.domain.model.TeamStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * UseCase pur — modèle Dixon-Coles (Poisson + correction τ + time decay).
 *
 * - Forces domicile/extérieur depuis les 10 derniers matchs pondérés par
 *   exp(-ξ × Δt), ξ = 0.0016 (décroissance journalière standard) ;
 *   à défaut, agrégats de saison ; à défaut, forces neutres = 1.0.
 * - τ(x,y) correction sur les 4 scores faibles (0-0, 0-1, 1-0, 1-1)
 * - ρ défaut = -0.13
 * - Troncature [0..6] puis normalisation
 * - Aucune dépendance Android / I/O
 */
class CalculateDixonColesUseCase @Inject constructor() {

    companion object {
        private const val XI = 0.0016          // time decay par jour
        private const val RHO_DEFAULT = -0.13
        private const val HOME_ADVANTAGE = 1.15
        private const val LEAGUE_AVG_FALLBACK = 1.35
        private const val MAX_GOALS = 6
        /**
         * Recalibrage 2026-10-13 (export CSV 60 résolus, 50,0 %) : pénalité
         * anti-biais offensif de 15 % sur le BTTS-Yes. Les blocs bas de début
         * de saison (ex. Liverpool 0-0 Fulham, 0-0 multiples) ont gonflé les
         * xG offensifs ; on atténue donc P(BTTS-Yes) d’un facteur 0,85.
         * Constante explicite et testée, jamais de valeur magique dispersée.
         */
        const val BTTS_XG_PENALTY = 0.85
    }

    suspend operator fun invoke(
        match: Match,
        homeStats: TeamStats,
        awayStats: TeamStats,
        leagueAverageGoals: Double = LEAGUE_AVG_FALLBACK,
        rho: Double = RHO_DEFAULT,
        homeRecent: List<RecentMatch> = emptyList(),
        awayRecent: List<RecentMatch> = emptyList()
    ): MatchPrediction = withContext(Dispatchers.Default) {

        val avg = if (leagueAverageGoals > 0) leagueAverageGoals else LEAGUE_AVG_FALLBACK

        val attackHome = recentForce(pickVenue(homeRecent, true), RecentMatch::goalsFor, avg)
            ?: computeForce(
                goals = homeStats.goalsForHome,
                matches = homeStats.playedHome,
                leagueAvg = avg
            )
        val defenseHome = recentForce(pickVenue(homeRecent, true), RecentMatch::goalsAgainst, avg)
            ?: computeForce(
                goals = homeStats.goalsAgainstHome,
                matches = homeStats.playedHome,
                leagueAvg = avg
            )
        val attackAway = recentForce(pickVenue(awayRecent, false), RecentMatch::goalsFor, avg)
            ?: computeForce(
                goals = awayStats.goalsForAway,
                matches = awayStats.playedAway,
                leagueAvg = avg
            )
        val defenseAway = recentForce(pickVenue(awayRecent, false), RecentMatch::goalsAgainst, avg)
            ?: computeForce(
                goals = awayStats.goalsAgainstAway,
                matches = awayStats.playedAway,
                leagueAvg = avg
            )

        val lambdaHome = (attackHome * defenseAway * avg * HOME_ADVANTAGE).coerceIn(0.05, 6.0)
        val lambdaAway = (attackAway * defenseHome * avg).coerceIn(0.05, 6.0)

        // Contrainte mathématique sur ρ
        val safeRho = rho.coerceIn(
            maxOf(-1.0 / lambdaHome, -1.0 / lambdaAway),
            minOf(1.0 / (lambdaHome * lambdaAway), 1.0)
        )

        var pHome = 0.0
        var pDraw = 0.0
        var pAway = 0.0
        var pOver25 = 0.0
        var pBttsYes = 0.0
        var totalMass = 0.0

        for (x in 0..MAX_GOALS) {
            val px = poisson(x, lambdaHome)
            for (y in 0..MAX_GOALS) {
                val py = poisson(y, lambdaAway)
                val tau = tau(x, y, lambdaHome, lambdaAway, safeRho)
                val p = px * py * tau
                totalMass += p
                when {
                    x > y -> pHome += p
                    x == y -> pDraw += p
                    else -> pAway += p
                }
                if (x + y > 2) pOver25 += p
                if (x >= 1 && y >= 1) pBttsYes += p
            }
        }

        // Normalisation
        if (totalMass > 0) {
            pHome /= totalMass
            pDraw /= totalMass
            pAway /= totalMass
            pOver25 /= totalMass
            pBttsYes /= totalMass
        }

        // Pénalité anti-biais offensif (recalibrage 2026-10-13) : P(BTTS-Yes)
        // réduite de 15 % avant écrêtage. P(BTTS-No) = 1 − P ajustée.
        val bttsYesAdj = (pBttsYes * BTTS_XG_PENALTY).coerceIn(0.0, 1.0)

        MatchPrediction(
            matchId = match.id,
            homeWinProb = pHome,
            drawProb = pDraw,
            awayWinProb = pAway,
            over25Prob = pOver25.coerceIn(0.0, 1.0),
            under25Prob = (1.0 - pOver25).coerceIn(0.0, 1.0),
            lambdaHome = lambdaHome,
            lambdaAway = lambdaAway,
            bttsYesProb = bttsYesAdj,
            bttsNoProb = (1.0 - bttsYesAdj).coerceIn(0.0, 1.0)
        )
    }

    private fun computeForce(goals: Int, matches: Int, leagueAvg: Double): Double {
        if (matches < 5) return 1.0
        val teamAvg = goals.toDouble() / matches
        return (teamAvg / leagueAvg).coerceIn(0.3, 3.0)
    }

    /**
     * Garde les matchs au bon domicile quand l’échantillon suffit (≥ 2),
     * sinon utilise toute la forme récente (début de saison).
     */
    private fun pickVenue(entries: List<RecentMatch>, homeOnly: Boolean): List<RecentMatch> {
        val venue = entries.filter { it.wasHome == homeOnly }
        return if (venue.size >= 2) venue else entries
    }

    /**
     * Force d’équipe depuis la forme récente pondérée par time decay.
     * Retourne null si aucune ligne exploitable (→ repli agrégats saison).
     */
    private fun recentForce(
        entries: List<RecentMatch>,
        goals: (RecentMatch) -> Int,
        leagueAvg: Double
    ): Double? {
        if (entries.isEmpty()) return null
        var num = 0.0
        var den = 0.0
        for (e in entries) {
            val w = exp(-XI * e.daysAgo.coerceAtLeast(0.0))
            num += w * goals(e)
            den += w
        }
        if (den <= 0.0) return null
        return ((num / den) / leagueAvg).coerceIn(0.3, 3.0)
    }

    /** τ(x, y) — correction Dixon-Coles */
    private fun tau(x: Int, y: Int, lambdaH: Double, lambdaA: Double, rho: Double): Double =
        when {
            x == 0 && y == 0 -> 1.0 - lambdaH * lambdaA * rho
            x == 0 && y == 1 -> 1.0 + lambdaH * rho
            x == 1 && y == 0 -> 1.0 + lambdaA * rho
            x == 1 && y == 1 -> 1.0 - rho
            else -> 1.0
        }

    private fun poisson(k: Int, lambda: Double): Double {
        if (lambda <= 0.0) return if (k == 0) 1.0 else 0.0
        var fact = 1.0
        for (i in 2..k) fact *= i
        return exp(-lambda) * lambda.pow(k) / fact
    }
}
