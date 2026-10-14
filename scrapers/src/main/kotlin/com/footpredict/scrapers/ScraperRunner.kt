package com.footpredict.scrapers

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Gestionnaire de parallélisme v2 : exécute `predict()` sur tous les scrapers
 * SIMULTANÉMENT, ignore proprement les erreurs (403, timeout, null) et ne
 * retourne que les pronostics exploitables.
 *
 * Garanties : un site en échec (exception, timeout [perSiteTimeoutMs],
 * null) n’affecte ni les autres ni le résultat global.
 */
class ScraperRunner(
    private val perSiteTimeoutMs: Long = 20_000L,
    private val includeBlocked: Boolean = false
) {
    suspend fun runAll(
        metadata: MatchMetadata,
        scrapers: List<FootballScraper>
    ): List<ScraperPrediction> =
        supervisorScope {
            scrapers
                .filter { includeBlocked || it.feasibility != Feasibility.BLOCKED }
                .map { scraper ->
                    async {
                        withTimeoutOrNull(perSiteTimeoutMs) {
                            runCatching { scraper.predict(metadata) }.getOrNull()
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
        }
}
