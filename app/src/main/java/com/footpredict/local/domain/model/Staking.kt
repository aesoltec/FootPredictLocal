package com.footpredict.local.domain.model

import kotlin.math.min

/**
 * Stratégies de mise — gestion du capital v1.3.0 (pur, testé).
 *
 * - [FLAT] : mise fixe (10 € par défaut), écrêtée au capital restant.
 * - [KELLY] : fraction de Kelly sur la probabilité et la cote décimale,
 *   plafonnée à 5 % du capital ([KELLY_MAX_FRACTION]) ; 0 € si aucun
 *   avantage (fraction ≤ 0), jamais plus que le capital, jamais de mise
 *   négative. Approximation documentée : pour un combiné, la confiance
 *   moyenne et la cote totale sont traitées comme un pari unique.
 */
enum class StakingStrategy { FLAT, KELLY }

const val FLAT_DEFAULT_STAKE = 10.0
const val KELLY_MAX_FRACTION = 0.05
const val DEFAULT_BANKROLL = 500.0

/** Fraction de Kelly : f = (p·b − (1−p)) / b, b = cote − 1. ≤ 0 = sans mise. */
fun kellyFraction(probability: Double, decimalOdds: Double): Double {
    if (decimalOdds <= 1.0 || probability <= 0.0 || probability >= 1.0) return 0.0
    val b = decimalOdds - 1.0
    return ((probability * b - (1.0 - probability)) / b).coerceAtLeast(0.0)
}

/** Mise Kelly en euros, plafonnée à 5 % du capital (0 € sans avantage). */
fun kellyStake(
    probability: Double,
    decimalOdds: Double,
    capital: Double,
    maxFraction: Double = KELLY_MAX_FRACTION
): Double {
    if (capital <= 0.0) return 0.0
    return capital * min(kellyFraction(probability, decimalOdds), maxFraction)
}

/**
 * Mise recommandée en euros selon la stratégie (pure, testée).
 * Toujours dans [0, capital] ; Kelly → 0 € si pas d’avantage.
 */
fun recommendedStake(
    strategy: StakingStrategy,
    probability: Double,
    decimalOdds: Double,
    capital: Double,
    flatStake: Double = FLAT_DEFAULT_STAKE
): Double {
    if (capital <= 0.0) return 0.0
    return when (strategy) {
        StakingStrategy.FLAT -> flatStake.coerceIn(0.0, capital)
        StakingStrategy.KELLY -> kellyStake(probability, decimalOdds, capital)
    }
}
