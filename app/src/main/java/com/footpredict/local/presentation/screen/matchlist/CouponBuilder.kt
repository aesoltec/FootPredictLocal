package com.footpredict.local.presentation.screen.matchlist

import com.footpredict.local.domain.model.EnrichedMatch
import com.footpredict.local.domain.model.EnsemblePrediction
import com.footpredict.local.domain.model.oddsInRange

/**
 * Jambe d’un combiné : un marché d’un match avec sa probabilité et,
 * si disponibles, sa cote bookmaker. Sans cotes → probabilité seule
 * (affichée « Statistique Pure », jamais de décimale inventée).
 */
data class CouponLeg(
    val matchId: Int,
    val label: String,
    val probability: Double,
    val decimalOdds: Double? = null
)

/** Combiné du jour : 2–8 jambes. Cote totale réelle, ou null si sans cotes. */
data class Coupon(
    val legs: List<CouponLeg>,
    val totalOdds: Double?,
    val avgConfidence: Double
) {
    init {
        require(legs.size in 2..8) { "Combiné : 2 à 8 jambes" }
        totalOdds?.let { require(it in 4.0..28.0) { "Cote totale hors [4.0, 28.0]" } }
    }
}

/** Ligne du Safe : marché dégradé + probabilité (cote estimée = 1/p). */
data class SafeLeg(
    val matchId: Int,
    val label: String,
    val probability: Double
)

/** Safe du jour : 2–4 lignes, sans cotes scrapées (estimées affichées). */
data class SafeCoupon(
    val legs: List<SafeLeg>,
    val avgConfidence: Double
) {
    init {
        require(legs.size in 2..4) { "Safe : 2 à 4 jambes" }
    }
}

/**
 * Construit le coupon du jour (pur, testé) : jambes = meilleurs picks.
 * - AVEC cotes : edge > 0 requis, produit dans [4.0, 28.0].
 * - SANS cotes : probabilité seule (confiance ≥ 75 % filtrée en amont),
 *   produit des cotes estimées (1/p) borné [4.0, 28.0] pour rester
 *   dans un raisonnable statistique (affiché « Statistique Pure »).
 * Null si rien ne passe (jamais de proposition forcée).
 */
fun buildCoupon(
    matches: List<EnrichedMatch>,
    predictions: Map<Int, EnsemblePrediction>,
    minLegProb: Double = 0.0,
    minTotalOdds: Double = 4.0,
    maxTotalOdds: Double = 28.0,
    maxLegs: Int = 8
): Coupon? {
    data class Cand(
        val matchId: Int,
        val label: String,
        val prob: Double,
        val odds: Double,
        val edge: Double
    )
    val cands = mutableListOf<Cand>()
    for (em in matches) {
        val pred = predictions[em.fixture.id] ?: continue
        val f = em.fixture
        val odds = em.odds
        // Sides : avec cotes réelles si disponibles, sinon probabilités seules.
        val sides = if (odds != null) {
            listOfNotNull(
                odds.homeOdds?.takeIf { it > 1.0 }?.let {
                    Triple("Victoire ${f.homeTeamName}", pred.homeWinProb, it)
                },
                odds.drawOdds?.takeIf { it > 1.0 }?.let {
                    Triple("Match nul", pred.drawProb, it)
                },
                odds.awayOdds?.takeIf { it > 1.0 }?.let {
                    Triple("Victoire ${f.awayTeamName}", pred.awayWinProb, it)
                },
                pred.over25Prob?.takeIf { it > com.footpredict.local.domain.model.OVER25_MIN_PROB }?.let { over ->
                    odds.over25Odds?.takeIf { it > 1.0 }?.let { o ->
                        Triple("Plus de 2,5 buts", over, o)
                    }
                },
                pred.over25Prob?.let { over ->
                    odds.under25Odds?.takeIf { it > 1.0 }?.let { u ->
                        Triple("Moins de 2,5 buts", 1.0 - over, u)
                    }
                }
            )
        } else {
            // Sans cotes : probabilités seules (meilleur pick du match).
            val best = com.footpredict.local.domain.model.bestMarketPick(
                pred.homeWinProb, pred.drawProb, pred.awayWinProb,
                pred.over25Prob, pred.bttsYesProb
            )
            val label = when (best.kind) {
                com.footpredict.local.domain.model.MarketKind.HOME_WIN -> "Victoire ${f.homeTeamName}"
                com.footpredict.local.domain.model.MarketKind.AWAY_WIN -> "Victoire ${f.awayTeamName}"
                com.footpredict.local.domain.model.MarketKind.DRAW -> "Match nul"
                com.footpredict.local.domain.model.MarketKind.OVER25 -> "Plus de 2,5 buts"
                com.footpredict.local.domain.model.MarketKind.UNDER25 -> "Moins de 2,5 buts"
                com.footpredict.local.domain.model.MarketKind.BTTS_YES -> "Les deux marquent : oui"
                com.footpredict.local.domain.model.MarketKind.BTTS_NO -> "Les deux marquent : non"
                else -> "Victoire ${f.homeTeamName}"
            }
            listOf(Triple(label, best.probability, 1.0)) // odds=1.0 → pas de contribution
        }
        for ((label, prob, odd) in sides) {
            if (odd <= 1.0 || prob < minLegProb) continue
            // Edge vs implicite brute (marge incluse = estimation conservatrice).
            // Sans cotes (odd=1.0) : pas de filtre edge — probabilité seule.
            val edge = if (odd > 1.0) prob - 1.0 / odd else 0.0
            if (odd > 1.0 && edge <= 0) continue
            cands.add(Cand(f.id, label, prob, odd, edge))
        }
    }
    // Meilleur côté par match (un seul pari par match).
    val bestPerMatch = cands.groupBy { it.matchId }
        .mapValues { (_, v) -> v.maxBy { it.edge } }
        .values.sortedByDescending { it.prob }

    var best: Coupon? = null
    for (n in 2..maxLegs.coerceAtMost(8)) {
        if (bestPerMatch.size < n) continue
        for (combo in combinations(bestPerMatch, n)) {
            val total = combo.fold(1.0) { acc, c -> acc * c.odds }
            if (total < minTotalOdds || total > maxTotalOdds) continue
            val avg = combo.sumOf { it.prob } / n
            val coupon = Coupon(
                legs = combo.map { CouponLeg(it.matchId, it.label, it.prob, if (it.odds > 1.0) it.odds else null) },
                totalOdds = if (combo.all { it.odds > 1.0 }) total else null,
                avgConfidence = avg
            )
            if (best == null || avg > best.avgConfidence) best = coupon
        }
    }
    return best
}

/** Combinaisons sans allocation excessive (n ≤ 8, listes courtes en pratique). */
private fun <T> combinations(list: List<T>, n: Int): List<List<T>> {
    if (n == 0) return listOf(emptyList())
    if (list.size < n) return emptyList()
    if (n == 1) return list.map { listOf(it) }
    val out = mutableListOf<List<T>>()
    for (i in list.indices) {
        for (rest in combinations(list.drop(i + 1), n - 1)) {
            out.add(listOf(list[i]) + rest)
        }
    }
    return out
}

/**
 * Construit le Safe du jour (pur, testé) : pour chaque match, le meilleur
 * pick est dégradé vers sa ligne sécurisée ([safeDowngrade]) ; seules les
 * lignes dont la cote ESTIMÉE (1/p) est dans [1.5, 5.0] participent
 * (exigence 2026-10-14 : cote affichée mais sans scrape → « estimée »),
 * combinaisons 2–4, gagnante = fiabilité moyenne max.
 * Null si rien ne passe (jamais forcé — à ces bornes, le vide est normal).
 */
fun buildSafeCoupon(
    matches: List<EnrichedMatch>,
    predictions: Map<Int, EnsemblePrediction>,
    maxLegs: Int = com.footpredict.local.domain.model.SAFE_MAX_LEGS,
    minOdds: Double = 1.5,
    maxOdds: Double = 5.0
): SafeCoupon? {
    data class Cand(
        val matchId: Int,
        val label: String,
        val prob: Double
    )
    val cands = mutableListOf<Cand>()
    for (em in matches) {
        val pred = predictions[em.fixture.id] ?: continue
        val best = com.footpredict.local.domain.model.bestMarketPick(
            pred.homeWinProb, pred.drawProb, pred.awayWinProb,
            pred.over25Prob, pred.bttsYesProb
        )
        val safe = com.footpredict.local.domain.model.safeDowngrade(
            best, pred.homeWinProb, pred.drawProb, pred.awayWinProb,
            pred.over15Prob, pred.over35Prob
        )
        if (safe == null) continue
        // Cote estimée (1/p) dans [1.5, 5.0] : seul filtre (remplace l'ancien
        // seuil 88 % — les deux étaient contradictoires : p ≥ 0,88 ⟺ cote ≤ 1,14).
        if (!oddsInRange(safe.probability, minOdds, maxOdds)) continue
        val f = em.fixture
        val label = when (safe.kind) {
            com.footpredict.local.domain.model.SafeKind.OVER15 -> "Plus de 1,5 but"
            com.footpredict.local.domain.model.SafeKind.UNDER35 -> "Moins de 3,5 buts"
            com.footpredict.local.domain.model.SafeKind.DC_1X ->
                "${f.homeTeamName} ou nul"
            com.footpredict.local.domain.model.SafeKind.DC_12 -> "Pas de nul"
            com.footpredict.local.domain.model.SafeKind.DC_X2 ->
                "Nul ou ${f.awayTeamName}"
        }
        cands += Cand(f.id, label, safe.probability)
    }
    // Meilleure ligne par match (un seul pari par match).
    val bestPerMatch = cands.groupBy { it.matchId }
        .mapValues { (_, v) -> v.maxBy { it.prob } }
        .values.sortedByDescending { it.prob }

    var best: SafeCoupon? = null
    for (n in 2..maxLegs.coerceAtMost(4)) {
        if (bestPerMatch.size < n) continue
        for (combo in combinations(bestPerMatch, n)) {
            val avg = combo.sumOf { it.prob } / n
            val coupon = SafeCoupon(
                legs = combo.map { SafeLeg(it.matchId, it.label, it.prob) },
                avgConfidence = avg
            )
            if (best == null || avg > best.avgConfidence) best = coupon
        }
    }
    return best
}
