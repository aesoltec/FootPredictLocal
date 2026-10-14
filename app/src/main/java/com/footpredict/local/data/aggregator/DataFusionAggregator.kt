package com.footpredict.local.data.aggregator

import com.footpredict.local.data.local.entity.MatchEntity
import com.footpredict.local.data.local.entity.OddsEntity
import com.footpredict.local.data.local.entity.TeamStatsEntity
import com.footpredict.local.data.remote.fixture.FixtureRemoteDataSource
import com.footpredict.local.data.remote.odds.OddsRemoteDataSource
import com.footpredict.local.data.remote.stats.StatsRemoteDataSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * Résultat de fusion : gagnants + sources + lignes non retenues
 * (ex. xG Understat quand une API officielle gagne).
 */
data class MergedFixtures(
    val fixtures: List<MatchEntity>,
    val sourcesUsed: List<String>,
    val alternates: List<MatchEntity> = emptyList()
)

/**
 * Recopie le xG des lignes alternatives vers les gagnants sans xG.
 * Appariement : mêmes équipes normalisées + date à ±7 jours (reports,
 * fuseaux, rythmes de mise à jour). Pur et testé ; ne touche jamais
 * aux scores/statuts des gagnants.
 */
fun mergeXg(winners: List<MatchEntity>, alternates: List<MatchEntity>): List<MatchEntity> {
    if (winners.isEmpty() || alternates.isEmpty()) return winners
    return winners.map { w ->
        if (w.xgHome != null && w.xgAway != null) return@map w
        val hit = alternates.firstOrNull { a ->
            (a.xgHome != null || a.xgAway != null) &&
                kotlin.math.abs(a.dateTimestamp - w.dateTimestamp) < 7 * 86400 &&
                namesMatch(a.homeTeamName, w.homeTeamName) &&
                namesMatch(a.awayTeamName, w.awayTeamName)
        } ?: return@map w
        w.copy(
            xgHome = w.xgHome ?: hit.xgHome,
            xgAway = w.xgAway ?: hit.xgAway
        )
    }
}

private fun norm(name: String): String =
    name.lowercase()
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun namesMatch(a: String, b: String): Boolean {
    val x = norm(a)
    val y = norm(b)
    if (x == y) return true
    if (x.length <= 3 || y.length <= 3) return false
    if (x.contains(y) || y.contains(x)) return true
    val tx = x.split(" ").filter { it.length > 1 }.toSet()
    val ty = y.split(" ").filter { it.length > 1 }.toSet()
    if (tx.isEmpty() || ty.isEmpty()) return false
    val inter = tx.intersect(ty).size
    return inter.toDouble() / (tx.size + ty.size - inter) >= 0.5
}

/**
 * Même match toutes sources confondues (pur, testé) : mêmes équipes (flou :
 * « Hull » == « Hull City AFC », « Brighton » == « Brighton & Hove Albion »)
 * + même jour calendaire (±1 j pour les fuseaux). Utilisé contre les doublons
 * inter-synchros : la meilleure source variant (quota, panne), des lignes
 * d’ID différents pour le même match s’accumuleraient sinon (alertes et
 * compteurs doublés, constaté sur téléphone).
 */
fun isSameFixture(a: MatchEntity, b: MatchEntity): Boolean {
    if (kotlin.math.abs(a.dateTimestamp - b.dateTimestamp) > 86400) return false
    return namesMatch(a.homeTeamName, b.homeTeamName) &&
        namesMatch(a.awayTeamName, b.awayTeamName)
}

/**
 * Déduplique en gardant la PREMIÈRE occurrence (pur, testé) : l’historique
 * soldé référence les IDs, on ne remplace donc jamais une ligne existante
 * par un doublon d’une autre source (pas de lignes orphelines).
 */
fun dedupeFixtures(fixtures: List<MatchEntity>): List<MatchEntity> {
    val kept = mutableListOf<MatchEntity>()
    for (f in fixtures) {
        if (kept.none { isSameFixture(it, f) }) kept += f
    }
    return kept
}

/**
 * Agrège les sources en parallèle et fusionne par reliabilityScore.
 *
 * Règles :
 * - Fixtures/scores → API-Football prioritaire
 * - xG → Understat (Supabase) prioritaire
 * - Cotes → Odds-API.io prioritaire, scraping en fallback
 * - Dégradation gracieuse : une source qui échoue n’empêche pas les autres
 */
class DataFusionAggregator @Inject constructor(
    private val fixtureSources: Set<@JvmSuppressWildcards FixtureRemoteDataSource>,
    private val statsSources: Set<@JvmSuppressWildcards StatsRemoteDataSource>,
    private val oddsSources: Set<@JvmSuppressWildcards OddsRemoteDataSource>
) {
    /**
     * Récupère et fusionne les fixtures de toutes les sources.
     * Retourne la liste issue de la source la plus fiable AYANT RÉPONDU
     * avec des données : une source vide (clé manquante, hors périmètre)
     * ne masque jamais une source moins fiable mais non vide.
     */
    suspend fun fetchMergedFixtures(leagueId: Int, season: Int): Pair<List<MatchEntity>, List<String>> =
        fetchMergedFixturesFull(leagueId, season).let { it.fixtures to it.sourcesUsed }

    /** Version complète : expose aussi les lignes non retenues (ex. xG). */
    suspend fun fetchMergedFixturesFull(leagueId: Int, season: Int): MergedFixtures =
        coroutineScope {
            val results = fixtureSources.map { source ->
                async {
                    try {
                        source to source.fetchFixtures(leagueId, season)
                    } catch (e: Exception) {
                        null
                    }
                }
            }.awaitAll().filterNotNull()

            if (results.isEmpty()) {
                return@coroutineScope MergedFixtures(emptyList(), emptyList(), emptyList())
            }

            val nonEmpty = results.filter { it.second.isNotEmpty() }
            // Si toutes les sources sont vides (hors-ligne + cache vide),
            // liste vide honnête : le repository émettra "Aucune source
            // disponible" au lieu de planter sur maxByOrNull!! (audit 2026-10-14).
            if (nonEmpty.isEmpty()) {
                return@coroutineScope MergedFixtures(emptyList(), emptyList(), emptyList())
            }
            val best = nonEmpty.maxByOrNull { it.first.reliabilityScore }
                ?: return@coroutineScope MergedFixtures(emptyList(), emptyList(), emptyList())
            val sourcesUsed = results.map { it.first.sourceName }
            val alternates = results
                .filter { it.first.sourceName != best.first.sourceName }
                .flatMap { it.second }
            MergedFixtures(best.second, sourcesUsed, alternates)
        }

    /**
     * Fusionne les stats d’une équipe (xG prioritaire depuis Understat).
     */
    suspend fun fetchMergedTeamStats(
        teamId: Int,
        leagueId: Int,
        season: Int
    ): TeamStatsEntity? = coroutineScope {
        val results = statsSources.map { source ->
            async {
                try {
                    source to source.fetchTeamStats(teamId, leagueId, season)
                } catch (e: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull().filter { it.second != null }

        if (results.isEmpty()) return@coroutineScope null

        // Priorité xG (reliability 0.9) puis API-Football
        val sorted = results.sortedByDescending { it.first.reliabilityScore }
        val base = sorted.firstOrNull()?.second ?: return@coroutineScope null

        // Enrichir avec xG si une source xG a répondu
        val xgSource = sorted.find { it.first.sourceName.contains("understat") }?.second
        if (xgSource != null && xgSource !== base) {
            base.copy(
                xgForHome = xgSource.xgForHome ?: base.xgForHome,
                xgForAway = xgSource.xgForAway ?: base.xgForAway,
                xgaHome = xgSource.xgaHome ?: base.xgaHome,
                xgaAway = xgSource.xgaAway ?: base.xgaAway,
                npxgFor = xgSource.npxgFor ?: base.npxgFor
            )
        } else base
    }

    /**
     * Fusionne les cotes (Odds-API prioritaire, scraping fallback).
     */
    suspend fun fetchMergedOdds(matchId: Int): OddsEntity? = coroutineScope {
        val results = oddsSources.map { source ->
            async {
                try {
                    source to source.fetchOdds(matchId)
                } catch (e: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull().filter { it.second != null }

        if (results.isEmpty()) return@coroutineScope null
        results.maxByOrNull { it.first.reliabilityScore }?.second
    }

    /**
     * Stats de toute la ligue en un minimum d’appels (vital sur connexion
     * lente). Sources essayées par fiabilité décroissante, première non vide.
     */
    suspend fun fetchLeagueStatsBatch(leagueId: Int, season: Int): List<TeamStatsEntity> {
        for (source in statsSources.sortedByDescending { it.reliabilityScore }) {
            try {
                val rows = source.fetchLeagueStats(leagueId, season)
                if (rows.isNotEmpty()) return rows
            } catch (_: Exception) {
            }
        }
        return emptyList()
    }

    /**
     * Cotes groupées par match (1 appel au lieu de N).
     */
    suspend fun fetchOddsBatch(): Map<Int, OddsEntity> {
        for (source in oddsSources.sortedByDescending { it.reliabilityScore }) {
            try {
                val rows = source.fetchAllOdds()
                if (rows.isNotEmpty()) return rows
            } catch (_: Exception) {
            }
        }
        return emptyMap()
    }
}
