package com.footpredict.scrapers

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Contrat que chaque scraper de site implémente.
 *
 * @param matchId identifiant universel « HOME|AWAY|AAAA-MM-JJ » (noms bruts,
 * chaque scraper normalise et résout dans son propre référentiel).
 */
interface FootballScraper {
    val sourceName: String

    /** Fiabilité 0..1 (table de faisabilité dans README-scrapers.md). */
    val reliability: Double

    /** Verdict de faisabilité honnête : ce que le scraper peut réellement faire. */
    val feasibility: Feasibility

    suspend fun fetchData(matchId: String): MatchData?

    /** Pronostic explicite si le site en publie un, null sinon (cas général). */
    suspend fun fetchPrediction(matchId: String): Prediction? = null

    /**
     * API v2 : pronostic standardisé à partir des métadonnées universelles.
     * Défaut null (la plupart des sites ne fournissent que des stats).
     */
    suspend fun predict(metadata: MatchMetadata): ScraperPrediction? = null
}

enum class Feasibility {
    /** Vérifié fonctionnel en HTTP pur (sonde le 2026-10-09). */
    SUPPORTED,

    /** Techniquement possible mais fragile/non vérifié (sélecteurs HTML). */
    PARTIAL,

    /** Bloqué : Cloudflare/JS/403 mesuré ou paywall — ne pas appeler en prod. */
    BLOCKED
}

/**
 * Base HTTP sans dépendance (JDK 11+) : rotation d’User-Agents, délai aléatoire
 * avant chaque appel, intervalle minimal par hôte, timeouts, 1 retry sur
 * 429/5xx avec backoff. En cas d’échec persistant : exception → isolée par
 * l’orchestrateur, jamais de crash global.
 */
abstract class BaseHttpScraper : FootballScraper {

    /** Délai minimal entre deux appels au même hôte (anti-bannissement). */
    protected open val minHostIntervalMs: Long = 1_000L

    /** Timeout global par tentative. */
    protected open val requestTimeout: Duration = Duration.ofSeconds(15)

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val lastCallPerHost = ConcurrentHashMap<String, Long>()

    /** En-têtes supplémentaires (ex. jeton tournant FotMob). */
    protected open fun extraHeaders(): Map<String, String> = emptyMap()

    /** Observabilité des réponses (ex. headers de throttling). */
    protected open fun onResponseHeaders(headers: Map<String, List<String>>) {}

    protected suspend fun getText(url: String, referer: String? = null): String {
        // Délai aléatoire 0,5–2 s + respect de l’intervalle par hôte.
        kotlinx.coroutines.delay(Random.nextLong(500, 2000))
        val host = URI(url).host
        val now = System.currentTimeMillis()
        val wait = minHostIntervalMs - (now - (lastCallPerHost[host] ?: 0L))
        if (wait > 0) kotlinx.coroutines.delay(wait)

        var attempt = 0
        while (true) {
            attempt++
            val builder = HttpRequest.newBuilder(URI(url))
                .timeout(requestTimeout)
                .header("User-Agent", UserAgents.random())
                .header("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
            if (referer != null) builder.header("Referer", referer)
            for ((k, v) in extraHeaders()) builder.header(k, v)
            // Certains endpoints AJAX exigent cet en-tête (ex. Understat).
            if (url.contains("getLeagueData")) {
                builder.header("X-Requested-With", "XMLHttpRequest")
            }
            val response = client.send(
                builder.GET().build(),
                HttpResponse.BodyHandlers.ofString()
            )
            lastCallPerHost[host] = System.currentTimeMillis()
            // Non protégé volontairement : un dépassement de quota DOIT
            // interrompre l’appel (remonté comme échec isolé).
            onResponseHeaders(response.headers().map())
            if (response.statusCode() == 200) return response.body()
            val retryable = response.statusCode() == 429 || response.statusCode() >= 500
            if (retryable && attempt == 1) {
                kotlinx.coroutines.delay(3000)
                continue
            }
            throw ScraperHttpException(url, response.statusCode())
        }
    }

    object UserAgents {
        private val POOL = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64; rv:127.0) Gecko/20100101 Firefox/127.0",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:127.0) Gecko/20100101 Firefox/127.0"
        )
        fun random(): String = POOL[Random.nextInt(POOL.size)]
    }
}

class ScraperHttpException(url: String, status: Int) :
    Exception("GET $url -> HTTP $status")
