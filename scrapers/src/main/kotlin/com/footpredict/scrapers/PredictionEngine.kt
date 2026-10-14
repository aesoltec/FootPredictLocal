package com.footpredict.scrapers

/**
 * Moteur de vote : agrège les pronostics EXPLICITES des sources
 * (peu de sites en publient — la modélisation statistique reste
 * Dixon-Coles côté app ; ce moteur est la couche consensus).
 *
 * Règles : ignore les échecs, vote majoritaire pondéré par fiabilité,
 * confiance = part du poids gagnant, avertissement si < 3 contributeurs.
 */
class PredictionEngine {

    data class FinalPrediction(
        val outcome: Outcome,
        /** Part du poids total derrière le vainqueur (0..1). */
        val confidence: Double,
        /** = confidence (part du consensus), exposé pour l’UI. */
        val agreement: Double,
        val over25Prob: Double?,
        val bttsYesProb: Double?,
        val contributors: Int,
        val sourcesUsed: List<String>,
        val warnings: List<String> = emptyList()
    )

    fun aggregate(results: List<ScrapeResult>): FinalPrediction? {
        val picks = results
            .filter { it.succeeded && it.prediction != null }
            .map { it.prediction!! }
            .filter { it.probability in 0.0..1.0 && it.weight > 0 }
        if (picks.isEmpty()) return null

        val totalWeight = picks.sumOf { it.weight }
        if (totalWeight <= 0) return null

        val byOutcome = picks.groupBy { it.outcome }
            .mapValues { (_, v) -> v.sumOf { it.weight } }
        val winner = byOutcome.maxBy { it.value }
        val confidence = (winner.value / totalWeight).coerceIn(0.0, 1.0)

        fun weightedAvg(selector: (Prediction) -> Double?): Double? {
            val valued = picks.mapNotNull { p -> selector(p)?.let { it to p.weight } }
                .filter { it.first in 0.0..1.0 }
            if (valued.isEmpty()) return null
            return valued.sumOf { it.first * it.second } / valued.sumOf { it.second }
        }

        val warnings = buildList {
            if (picks.size < 3) add("signal-faible:<3-contributeurs")
            if (confidence < 0.5) add("consensus-mou")
        }

        return FinalPrediction(
            outcome = winner.key,
            confidence = confidence,
            agreement = confidence,
            over25Prob = weightedAvg { it.over25Prob },
            bttsYesProb = weightedAvg { it.bttsYesProb },
            contributors = picks.size,
            sourcesUsed = picks.map { it.source }.distinct(),
            warnings = warnings
        )
    }

    /**
     * Agrégation v2 : vote pondéré sur pronostics standardisés avec
     * normalisation des équipes (aliases) et score de tendance par issue.
     * Les échecs (null) sont déjà filtrés par [ScraperRunner] ; double
     * sécurité ici via `filterNotNull` implicite (liste non-null en entrée).
     */
    fun aggregatePredictions(
        metadata: MatchMetadata,
        predictions: List<ScraperPrediction>
    ): AggregatedPrediction? = aggregateMarkets(metadata, predictions)?.let {
        AggregatedPrediction(
            metadata = metadata,
            outcome = it.outcome1X2.side,
            confidence = (it.outcome1X2.confidencePct / 100.0).coerceIn(0.0, 1.0),
            trendByOutcome = it.trendByOutcome,
            sourcesUsed = it.sourcesUsed,
            warnings = it.warnings
        )
    }

    /** Marché Over/Under 2.5 buts. */
    enum class OverUnderSide { OVER, UNDER }

    /** Consensus complet : 1X2 + Over/Under, confiances en % (0..100). */
    data class MarketConsensus(
        val metadata: MatchMetadata,
        val outcome1X2: MarketPick<Outcome>,
        val overUnder: MarketPick<OverUnderSide>?,
        /** Part de poids par issue 1X2 (somme = 1). */
        val trendByOutcome: Map<Outcome, Double>,
        val sourcesUsed: List<String>,
        val warnings: List<String> = emptyList()
    )

    /** Pick typé d’un marché : côté + indice de confiance en % (0..100). */
    data class MarketPick<T>(
        val side: T,
        /** Indice de confiance en % (0..100). */
        val confidencePct: Double
    )

    /**
     * Calcule l’indice de confiance en % pour la victoire 1/N/2 (vote pondéré
     * par fiabilité) et pour Over/Under 2.5 (moyenne pondérée).
     * Filtre : nulls, poids ≤ 0, confiances hors [0..1]. Null si inexploitable.
     */
    fun aggregateMarkets(
        metadata: MatchMetadata,
        predictions: List<ScraperPrediction?>
    ): MarketConsensus? {
        val valid = predictions.filterNotNull()
            .filter { it.confidence in 0.0..1.0 && it.weight > 0 }
        if (valid.isEmpty()) return null
        val totalWeight = valid.sumOf { it.weight }
        if (totalWeight <= 0) return null

        // — Victoire 1 / N / 2 : vote pondéré, confiance = part du gagnant. —
        val trend = Outcome.entries.associateWith { o ->
            valid.filter { it.outcome == o }.sumOf { it.weight } / totalWeight
        }
        val winning = trend.maxBy { it.value }
        val pick12 = MarketPick(winning.key, (winning.value * 100).coerceIn(0.0, 100.0))

        // — Over / Under 2.5 : moyenne pondérée, côté et confiance du max. —
        val overVotes = valid.mapNotNull { p ->
            p.over25Prob?.takeIf { it in 0.0..1.0 }?.let { it to p.weight }
        }
        val overUnder = if (overVotes.isEmpty()) {
            null
        } else {
            val over = overVotes.sumOf { it.first * it.second } / overVotes.sumOf { it.second }
            if (over >= 0.5) MarketPick(OverUnderSide.OVER, over * 100)
            else MarketPick(OverUnderSide.UNDER, (1.0 - over) * 100)
        }

        val warnings = buildList {
            if (valid.size < 3) add("signal-faible:<3-contributeurs")
            if (winning.value < 0.5) add("consensus-mou")
        }
        return MarketConsensus(
            metadata = metadata,
            outcome1X2 = pick12,
            overUnder = overUnder,
            trendByOutcome = trend,
            sourcesUsed = valid.map { it.source }.distinct(),
            warnings = warnings
        )
    }
}
