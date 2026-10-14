package com.footpredict.scrapers

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Lancement SIMULTANÉ de tous les scrapers (modèle demandé) avec isolation :
 * - [supervisorScope] : l’échec de l’un n’annule pas les autres ;
 * - timeout individuel [perSiteTimeoutMs] : un site lent ne bloque pas le lot ;
 * - les scrapers [Feasibility.BLOCKED] sont ignorés sauf [includeBlocked]
 *   (debug uniquement — ne jamais activer en production).
 */
class ScrapingOrchestrator(
    private val perSiteTimeoutMs: Long = 20_000L,
    private val includeBlocked: Boolean = false
) {
    suspend fun scrapeAll(matchId: String, scrapers: List<FootballScraper>): List<ScrapeResult> =
        supervisorScope {
            scrapers
                .filter { includeBlocked || it.feasibility != Feasibility.BLOCKED }
                .map { scraper ->
                    async {
                        val data = withTimeoutOrNull(perSiteTimeoutMs) {
                            runCatching { scraper.fetchData(matchId) }.getOrNull()
                        }
                        val prediction = if (data != null) {
                            withTimeoutOrNull(perSiteTimeoutMs) {
                                runCatching { scraper.fetchPrediction(matchId) }.getOrNull()
                            }
                        } else null
                        if (data == null) {
                            ScrapeResult(scraper.sourceName, scraper.reliability, error = "no-data")
                        } else {
                            ScrapeResult(scraper.sourceName, scraper.reliability, data, prediction)
                        }
                    }
                }
                .awaitAll()
        }
}
