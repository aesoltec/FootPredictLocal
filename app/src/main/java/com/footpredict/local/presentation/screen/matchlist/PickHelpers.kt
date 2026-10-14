package com.footpredict.local.presentation.screen.matchlist

import com.footpredict.local.domain.model.DivergenceLevel
import com.footpredict.local.domain.model.EnrichedMatch
import com.footpredict.local.domain.model.EnsemblePrediction

/**
 * Helpers purs de sélection des pronostics (testables sans Android).
 *
 * - Prédictions calculées uniquement pour les [MAX_PREDICTED] prochains matchs.
 * - Top 5 confiance / Top 5 à éviter affichés en sections dédiées.
 */
object PickHelpers {

    const val MAX_PREDICTED = 3
    const val MAX_TOP_PICKS = 5
    const val MAX_MARKET_PICKS = 2

    /** Noms d’affichage des compétitions (registre unique vérifié). */
    val LEAGUE_NAMES: Map<Int, String> =
        com.footpredict.local.data.remote.fixture.ApiFootballLeagues.names()

    private val PREMATCH = setOf("NS", "TBD", "PST")

    /** Tous les matchs à venir triés par date (sans plafond : vivier des tops). */
    fun allUpcoming(matches: List<EnrichedMatch>): List<EnrichedMatch> =
        matches.filter { it.fixture.statusShort in PREMATCH }
            .sortedBy { it.fixture.dateTimestamp }

    /**
     * Fenêtre temporelle stricte : par défaut [upcomingDays] à venir /
     * [pastDays] passés ; si [rangeMs] est défini (sélecteur de dates validé),
     * il remplace les fenêtres. `dateTs` en secondes, le reste en millisecondes.
     */
    fun inDateWindow(
        dateTs: Long,
        nowMs: Long,
        upcomingDays: Int,
        pastDays: Int,
        rangeMs: Pair<Long, Long>?
    ): Boolean {
        val tsMs = dateTs * 1000
        if (rangeMs != null) return tsMs in rangeMs.first..rangeMs.second
        val dayMs = 86_400_000L
        return tsMs in (nowMs - pastDays * dayMs)..(nowMs + upcomingDays * dayMs)
    }

    /** Prochains matchs à pronostiquer : NS triés par date, plafonnés. */
    fun topUpcoming(matches: List<EnrichedMatch>, n: Int = MAX_PREDICTED): List<EnrichedMatch> =
        allUpcoming(matches).take(n.coerceAtLeast(0))

    /** Top 5 : confiance la plus haute parmi les matchs pronostiqués. */
    fun topConfident(
        matches: List<EnrichedMatch>,
        predictions: Map<Int, EnsemblePrediction>,
        n: Int = MAX_TOP_PICKS
    ): List<TopPick> =
        matches.mapNotNull { em ->
            val pred = predictions[em.fixture.id] ?: return@mapNotNull null
            TopPick(
                matchId = em.fixture.id,
                homeTeam = em.fixture.homeTeamName,
                awayTeam = em.fixture.awayTeamName,
                dateTimestamp = em.fixture.dateTimestamp,
                outcome = dominantOutcome(pred),
                outcomeProb = dominantProb(pred),
                confidence = pred.confidence,
                divergent = pred.divergenceLevel == DivergenceLevel.HIGH
            )
        }.sortedByDescending { it.confidence }.take(n.coerceAtLeast(0))

    /**
     * Top 5 à éviter : divergence HIGH d’abord, puis confiance la plus basse.
     * Les matchs déjà présents dans les tops ([exclude]) en sont écartés :
     * un même match ne peut pas être à la fois « confiance » et « à éviter ».
     */
    fun toAvoid(        matches: List<EnrichedMatch>,
        predictions: Map<Int, EnsemblePrediction>,
        exclude: Set<Int> = emptySet(),
        n: Int = MAX_TOP_PICKS
    ): List<TopPick> =
        matches.mapNotNull { em ->
            if (em.fixture.id in exclude) return@mapNotNull null
            val pred = predictions[em.fixture.id] ?: return@mapNotNull null
            TopPick(
                matchId = em.fixture.id,
                homeTeam = em.fixture.homeTeamName,
                awayTeam = em.fixture.awayTeamName,
                dateTimestamp = em.fixture.dateTimestamp,
                outcome = dominantOutcome(pred),
                outcomeProb = dominantProb(pred),
                confidence = pred.confidence,
                divergent = pred.divergenceLevel == DivergenceLevel.HIGH
            )
        }.sortedWith(
            compareByDescending<TopPick> { it.divergent }.thenBy { it.confidence }
        ).take(n.coerceAtLeast(0))

    private fun dominantOutcome(pred: EnsemblePrediction): String = when {
        pred.homeWinProb >= pred.drawProb && pred.homeWinProb >= pred.awayWinProb -> "1"
        pred.awayWinProb >= pred.drawProb -> "2"
        else -> "X"
    }

    private fun dominantProb(pred: EnsemblePrediction): Double =
        maxOf(pred.homeWinProb, pred.drawProb, pred.awayWinProb)

    /**
     * Double chance déterministe depuis le 1X2 : P(1X) = P(1)+P(X),
     * P(12) = 1-P(X), P(X2) = P(X)+P(2). Somme > 1 par construction
     * (issues non exclusives) — c’est normal, ce sont des couvertures.
     */
    fun doubleChanceOf(home: Double, draw: Double, away: Double): DoubleChance {
        val p1X = (home + draw).coerceIn(0.0, 1.0)
        val p12 = (1.0 - draw).coerceIn(0.0, 1.0)
        val pX2 = (draw + away).coerceIn(0.0, 1.0)
        return DoubleChance(p1X = p1X, p12 = p12, pX2 = pX2)
    }

    /** Top Over 2.5 (seuil sécurité > 0,62, recalibré 2026-10-13) / Under / BTTS. */
    fun topOver25(
        matches: List<EnrichedMatch>,
        predictions: Map<Int, EnsemblePrediction>,
        n: Int = MAX_MARKET_PICKS
    ): List<TopPick> = marketRank(matches, predictions, n, "O25") {
        it.over25Prob?.takeIf { o -> o > com.footpredict.local.domain.model.OVER25_MIN_PROB }
    }

    fun topUnder25(
        matches: List<EnrichedMatch>,
        predictions: Map<Int, EnsemblePrediction>,
        n: Int = MAX_MARKET_PICKS
    ): List<TopPick> = marketRank(matches, predictions, n, "U25") { it.over25Prob?.let { o -> 1.0 - o } }

    fun topBttsYes(
        matches: List<EnrichedMatch>,
        predictions: Map<Int, EnsemblePrediction>,
        n: Int = MAX_MARKET_PICKS
    ): List<TopPick> = marketRank(matches, predictions, n, "BY") { it.bttsYesProb }

    fun topBttsNo(
        matches: List<EnrichedMatch>,
        predictions: Map<Int, EnsemblePrediction>,
        n: Int = MAX_MARKET_PICKS
    ): List<TopPick> = marketRank(matches, predictions, n, "BN") { it.bttsYesProb?.let { b -> 1.0 - b } }

    private fun marketRank(
        matches: List<EnrichedMatch>,
        predictions: Map<Int, EnsemblePrediction>,
        n: Int,
        code: String,
        value: (EnsemblePrediction) -> Double?
    ): List<TopPick> =
        matches.mapNotNull { em ->
            val pred = predictions[em.fixture.id] ?: return@mapNotNull null
            val v = value(pred) ?: return@mapNotNull null
            TopPick(
                matchId = em.fixture.id,
                homeTeam = em.fixture.homeTeamName,
                awayTeam = em.fixture.awayTeamName,
                dateTimestamp = em.fixture.dateTimestamp,
                outcome = code,
                outcomeProb = v,
                confidence = pred.confidence,
                divergent = false
            )
        }.sortedByDescending { it.outcomeProb }.take(n.coerceAtLeast(0))
}

/** Ligne d’un top (confiance ou à éviter). */
data class TopPick(
    val matchId: Int,
    val homeTeam: String,
    val awayTeam: String,
    val dateTimestamp: Long,
    val outcome: String,
    val outcomeProb: Double,
    val confidence: Double,
    val divergent: Boolean
)

/**
 * Double chance : couvertures 1X (domicile ou nul), 12 (pas de nul),
 * X2 (nul ou extérieur). Fortement typé pour l’affichage.
 */
data class DoubleChance(
    val p1X: Double,
    val p12: Double,
    val pX2: Double
) {
    /** Côté dominant : "1X", "12" ou "X2", avec sa probabilité. */
    fun best(): Pair<String, Double> = when (maxOf(p1X, p12, pX2)) {
        p12 -> "12" to p12
        pX2 -> "X2" to pX2
        else -> "1X" to p1X
    }
}

/** Force d’un pourcentage pour la couleur : vert > 70 %, orange 55–70 %. */
enum class Strength { STRONG, MEDIUM, LOW }

fun strengthOf(probability: Double): Strength = when {
    probability > 0.70 -> Strength.STRONG
    probability >= 0.55 -> Strength.MEDIUM
    else -> Strength.LOW
}

/**
 * Projection bankroll : mise [stake] par match sur [matches] matchs avec
 * [hitRate] mesuré et [avgOdd] moyenne saisie. Retourne (profit €, ROI %).
 * Hypothèse explicite : cotes homogènes à la moyenne saisie.
 */
fun bankrollProjection(
    hitRate: Double,
    matches: Int,
    stake: Double,
    avgOdd: Double
): Pair<Double, Double> {
    if (matches <= 0 || stake <= 0 || avgOdd <= 1.0) return 0.0 to 0.0
    val staked = matches * stake
    val returned = matches * hitRate.coerceIn(0.0, 1.0) * stake * avgOdd
    val profit = returned - staked
    return profit to profit / staked * 100
}

/**
 * Plus forte chute de cote 1X2 entre deux photos (> 10 % pour alerter).
 * Retourne (côté, pourcentage) ou null. Pur et testé.
 */
fun oddsDrop(
    old: com.footpredict.local.domain.model.Odds,
    new: com.footpredict.local.domain.model.Odds
): Pair<String, Double>? {
    val sides = listOfNotNull(
        old.homeOdds?.let { o -> new.homeOdds?.let { n -> Triple("1", o, n) } },
        old.drawOdds?.let { o -> new.drawOdds?.let { n -> Triple("X", o, n) } },
        old.awayOdds?.let { o -> new.awayOdds?.let { n -> Triple("2", o, n) } }
    )
    return sides.mapNotNull { (side, o, n) ->
        if (o > 0 && n > 0) {
            val drop = (o - n) / o * 100
            if (drop > 10.0) side to drop else null
        } else null
    }.maxByOrNull { it.second }
}

/** Top Double Chance : meilleur côté DC par match, 2 affichés max. */
fun topDoubleChance(
    matches: List<EnrichedMatch>,
    predictions: Map<Int, EnsemblePrediction>,
    n: Int = PickHelpers.MAX_MARKET_PICKS
): List<TopPick> =
    matches.mapNotNull { em ->
        val pred = predictions[em.fixture.id] ?: return@mapNotNull null
        val dc = PickHelpers.doubleChanceOf(pred.homeWinProb, pred.drawProb, pred.awayWinProb)
        val (code, prob) = dc.best()
        TopPick(
            matchId = em.fixture.id,
            homeTeam = em.fixture.homeTeamName,
            awayTeam = em.fixture.awayTeamName,
            dateTimestamp = em.fixture.dateTimestamp,
            outcome = "DC$code",
            outcomeProb = prob,
            confidence = pred.confidence,
            divergent = false
        )
    }.sortedByDescending { it.outcomeProb }.take(n.coerceAtLeast(0))

/**
 * Probabilités (app, marché) de l’issue favorite de l’ensemble.
 * Nulls si pas de prédiction ou pas de marché (affichage honnête partiel).
 */
fun favoriteVsMarket(
    pred: EnsemblePrediction?,
    market: com.footpredict.local.domain.model.MarketPrediction?
): Pair<Double?, Double?> {
    if (pred == null || market == null) return null to null
    return when {
        pred.homeWinProb >= pred.drawProb && pred.homeWinProb >= pred.awayWinProb ->
            pred.homeWinProb to market.homeProb
        pred.awayWinProb >= pred.drawProb ->
            pred.awayWinProb to market.awayProb
        else -> pred.drawProb to market.drawProb
    }
}

/**
 * H2H depuis des matchs terminés : victoires de [homeId] (où qu’il joue),
 * nuls, victoires de [awayId]. Scores manquants ignorés (jamais inventés).
 */
fun computeH2H(
    rows: List<com.footpredict.local.data.local.entity.MatchEntity>,
    homeId: Int,
    awayId: Int
): H2HStats {
    var hw = 0
    var d = 0
    var aw = 0
    for (m in rows) {
        val gh = m.goalsHome ?: continue
        val ga = m.goalsAway ?: continue
        val homeWon = if (m.homeTeamId == homeId) gh > ga else ga > gh
        val isDraw = gh == ga
        when {
            isDraw -> d++
            homeWon -> hw++
            else -> aw++
        }
    }
    return H2HStats(homeWins = hw, draws = d, awayWins = aw)
}
