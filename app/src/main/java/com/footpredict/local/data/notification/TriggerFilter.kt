package com.footpredict.local.data.notification

import com.footpredict.local.domain.model.EnrichedMatch
import com.footpredict.local.domain.model.EnsemblePrediction

/**
 * Filtre cumulé STRICT du déclencheur : un match ne notifie que s’il valide
 * TOUTES les conditions (ET logique). Pur et testé ; le Worker ne fait que
 * l’orchestration Android.
 */
object TriggerFilter {

    data class Thresholds(
        /** Ligues autorisées (ex. Top 5). */
        val leagues: Set<Int> = setOf(39, 140, 135, 78, 61),
        /** Confiance app minimale en % (ex. 70). */
        val minConfidencePct: Double = 70.0,
        /** Value bet minimale en points (ex. 0.15 = 15 %). */
        val minValueEdge: Double = 0.15
    )

    /**
     * Vrai si : ligue autorisée + match à venir + confiance ≥ seuil +
     * au moins un value bet ≥ seuil. Les seuils resolvables à défaut :
     * un match sans marché (edge null) ne passe jamais le filtre value.
     */
    fun passes(
        match: EnrichedMatch,
        prediction: EnsemblePrediction?,
        thresholds: Thresholds = Thresholds()
    ): Boolean {
        if (prediction == null) return false
        if (match.fixture.leagueId !in thresholds.leagues) return false
        if (match.fixture.statusShort == "FT") return false
        if (prediction.confidence * 100 < thresholds.minConfidencePct) return false
        val bestEdge = prediction.valueBets.maxOfOrNull { it.edge } ?: return false
        return bestEdge >= thresholds.minValueEdge
    }
}
