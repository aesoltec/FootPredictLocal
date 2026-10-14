package com.footpredict.local.domain.model

import kotlin.math.abs
import kotlin.math.min

/**
 * Estimation du marché Corners (ligne 9,5) — statistique pure, SANS cotes.
 *
 * Formule honnête documentée (v1.3.0) depuis l’enrichissement inter-sources :
 * - « Dangerosité Offensive » combinée = xG domicile + xG extérieur (match).
 * - Volume estimé = 4,5 + 1,6 × dangerosité (heuristique explicite, NON
 *   rétrotestée : aucun historique de corners sourcé à ce jour).
 * - Style de jeu (possession > 52 % des deux côtés, volume de tirs élevé) :
 *   paramètres OPTIONNELS — nos sources gratuites ne les fournissent pas
 *   aujourd’hui, donc bonus désactivé par défaut (jamais de valeur inventée).
 * - Décision : volume estimé > 9,5 avec confiance ≥ 65 % → « Plus de 9.5 » ;
 *   volume < 9,5 avec confiance ≥ 65 % → « Moins de 9.5 » ; sinon AUCUNE
 *   proposition (état honnête, pas de pick forcé).
 * - Confiance = 0,50 + 0,12 × |volume − 9,5|, écrêtée à 0,85 : seuls les
 *   matchs très ouverts ou très fermés proposent (conservateur).
 * - xG manquant (un seul côté suffit) → [hasEnoughData] = false, direction
 *   null, affichage « indisponible ». Cotes corners non scrapées → null
 *   graphique (« Cote : Statistique Pure »), jamais de 1,00 inventée.
 */
enum class CornersDirection { OVER, UNDER }

const val CORNERS_LINE = 9.5
const val CORNERS_MIN_CONFIDENCE = 0.65

data class CornersEstimate(
    val matchId: Int,
    val expectedCorners: Double?,
    val direction: CornersDirection?,
    /** 0..1, 0 si données insuffisantes. */
    val confidence: Double,
    val hasEnoughData: Boolean
)

fun estimateCorners(
    matchId: Int,
    xgHome: Double?,
    xgAway: Double?,
    possessionHome: Double? = null,
    possessionAway: Double? = null,
    shotsHome: Int? = null,
    shotsAway: Int? = null
): CornersEstimate {
    if (xgHome == null || xgAway == null) {
        return CornersEstimate(
            matchId = matchId,
            expectedCorners = null,
            direction = null,
            confidence = 0.0,
            hasEnoughData = false
        )
    }
    var volume = 4.5 + 1.6 * (xgHome + xgAway)
    // Bonus style de jeu : actif UNIQUEMENT si les 4 signaux sont fournis
    // (aujourd’hui jamais le cas avec nos sources gratuites).
    if (possessionHome != null && possessionAway != null &&
        possessionHome > 0.52 && possessionAway > 0.52
    ) {
        volume *= 1.08
    }
    if (shotsHome != null && shotsAway != null && shotsHome + shotsAway >= 25) {
        volume += 0.5
    }
    val confidence = (0.50 + 0.12 * abs(volume - CORNERS_LINE)).coerceIn(0.0, 0.85)
    val direction = when {
        confidence < CORNERS_MIN_CONFIDENCE -> null
        volume > CORNERS_LINE -> CornersDirection.OVER
        volume < CORNERS_LINE -> CornersDirection.UNDER
        else -> null
    }
    return CornersEstimate(
        matchId = matchId,
        expectedCorners = volume,
        direction = direction,
        confidence = if (direction != null) confidence else min(confidence, 0.64),
        hasEnoughData = true
    )
}

/** Ligne d’estimation corners pour l’onglet dédié (triée par confiance). */
data class CornersPick(
    val matchId: Int,
    val homeTeam: String,
    val awayTeam: String,
    val dateTimestamp: Long,
    val direction: CornersDirection,
    val expectedCorners: Double,
    val confidence: Double
)

/**
 * Estimations proposables uniquement (direction non null), triées par
 * confiance décroissante. Pur et testé.
 */
fun cornersPicks(matches: List<EnrichedMatch>): List<CornersPick> =
    matches.mapNotNull { em ->
        val f = em.fixture
        val est = estimateCorners(f.id, f.xgHome, f.xgAway)
        if (est.direction == null || est.expectedCorners == null) return@mapNotNull null
        CornersPick(
            matchId = f.id,
            homeTeam = f.homeTeamName,
            awayTeam = f.awayTeamName,
            dateTimestamp = f.dateTimestamp,
            direction = est.direction,
            expectedCorners = est.expectedCorners,
            confidence = est.confidence
        )
    }.sortedByDescending { it.confidence }
