package com.footpredict.local.presentation.screen.matchlist

import com.footpredict.local.domain.model.EnrichedMatch
import com.footpredict.local.domain.model.EnsemblePrediction

/**
 * Jambe d’un combiné : un marché d’un match avec sa cote bookmaker.
 * Les cotes DC n’existant pas chez nos sources, seules 1X2 et O/U
 * participent (jamais de décimales inventées).
 */
data class CouponLeg(
    val matchId: Int,
    val label: String,
    val probability: Double,
    val decimalOdds: Double
)

/** Combiné sécurisé : 2–4 jambes, cote totale dans [1.8, 5.0]. */
data class Coupon(
    val legs: List<CouponLeg>,
    val totalOdds: Double,
    val avgConfidence: Double
) {
    init {
        require(legs.size in 2..4) { "Combiné : 2 à 4 jambes" }
        require(totalOdds in 1.8..5.0) { "Cote totale hors [1.8, 5.0]" }
    }
}

/**
 * Safe du jour v1.3.2 : 2–4 lignes sécurisées à probabilité ≥ 88 %.
 * SANS cotes (non scrapées pour ces lignes) : pas de cote totale, jamais
 * de décimale inventée — uniquement probabilités + fiabilité moyenne.
 */
data class SafeLeg(
    val matchId: Int,
    val label: String,
    val probability: Double
)

data class SafeCoupon(
    val legs: List<SafeLeg>,
    val avgConfidence: Double
) {
    init {
        require(legs.size in 2..4) { "Safe : 2 à 4 jambes" }
    }
}

/**
 * Construit le meilleur combiné (pur, testé) : jambes = côtés 1X2/O-U
 * avec probabilité ≥ [minLegProb] ET edge > 0, combinaisons 2–4 dont le
 * produit est dans [1.8, 5.0], gagnant = confiance moyenne max.
 * Null si rien ne passe les filtres (jamais de proposition forcée).
 */
fun buildCoupon(
    matches: List<EnrichedMatch>,
    predictions: Map<Int, EnsemblePrediction>,
    minLegProb: Double = 0.0,
    minTotalOdds: Double = 1.8,
    maxTotalOdds: Double = 5.0
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
        val odds = em.odds ?: continue
        val f = em.fixture
        val sides = listOfNotNull(
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
                em.odds?.over25Odds?.takeIf { it > 1.0 }?.let { o ->
                    Triple("Plus de 2,5 buts", over, o)
                }
            },
            pred.over25Prob?.let { over ->
                em.odds?.under25Odds?.takeIf { it > 1.0 }?.let { u ->
                    Triple("Moins de 2,5 buts", 1.0 - over, u)
                }
            }
        )
        for ((label, prob, odd) in sides) {
            if (odd <= 1.0 || prob < minLegProb) continue
            // Edge vs implicite brute (marge incluse = estimation conservatrice).
            val edge = prob - 1.0 / odd
            if (edge > 0) cands.add(Cand(f.id, label, prob, odd, edge))
        }
    }
    // Meilleur côté par match (un seul pari par match).
    val bestPerMatch = cands.groupBy { it.matchId }
        .mapValues { (_, v) -> v.maxBy { it.edge } }
        .values.sortedByDescending { it.prob }

    var best: Coupon? = null
    for (n in 2..4) {
        if (bestPerMatch.size < n) continue
        for (combo in combinations(bestPerMatch, n)) {
            val total = combo.fold(1.0) { acc, c -> acc * c.odds }
            if (total < minTotalOdds || total > maxTotalOdds) continue
            val avg = combo.sumOf { it.prob } / n
            val coupon = Coupon(
                legs = combo.map { CouponLeg(it.matchId, it.label, it.prob, it.odds) },
                totalOdds = total,
                avgConfidence = avg
            )
            if (best == null || avg > best.avgConfidence) best = coupon
        }
    }
    return best
}

/** Combinaisons sans allocation excessive (n ≤ 4, listes courtes en pratique). */
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
 * lignes à probabilité ≥ [minSafeProb] (88 % exigé) participent ; combinaisons
 * 2–4, gagnante = fiabilité moyenne max. Null si rien ne passe (jamais forcé —
 * à 88 %, le vide est un résultat normal et honnête).
 */
fun buildSafeCoupon(
    matches: List<EnrichedMatch>,
    predictions: Map<Int, EnsemblePrediction>,
    minSafeProb: Double = com.footpredict.local.domain.model.SAFE_MIN_CONFIDENCE
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
        ) ?: continue
        if (safe.probability < minSafeProb) continue
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
    for (n in 2..4) {
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
