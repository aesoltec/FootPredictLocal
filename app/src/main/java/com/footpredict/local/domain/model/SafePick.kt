package com.footpredict.local.domain.model

/**
 * Marchés sécurisés du « Safe du jour » v1.3.2 (pur, testé) : chaque pari
 * principal se dégrade vers une ligne plus sûre, calculée par le moteur
 * (jamais de cote inventée — les cotes de ces lignes n’étant pas scrapées,
 * le Safe n’affiche aucune cote, seulement des probabilités).
 *
 * Table de dégradation (exigences produit) :
 * - Over 2.5 → Plus de 1,5 but (P(total > 1,5) Dixon-Coles)
 * - Under 2.5 → Moins de 3,5 buts (1 − P(total > 3,5))
 * - BTTS Yes → Plus de 1,5 but (« 1,5 but » : le total couvre le besoin)
 * - BTTS No → Moins de 3,5 buts (même profil défensif, ligne plus sûre)
 * - Victoire domicile → Double chance 1X ; extérieur → X2 ; nul → 12 (pas de nul)
 */
enum class SafeKind { OVER15, UNDER35, DC_1X, DC_12, DC_X2 }

/** Seuil du Safe : probabilité ≥ 88 % (exigence produit). */
const val SAFE_MIN_CONFIDENCE = 0.88

/** Plafond de jambes du Safe : 4 (exigence 2026-10-14). */
const val SAFE_MAX_LEGS = 4

/** Ligne sécurisée : marché dégradé + sa probabilité (cote estimée = 1/p). */
data class SafePick(val kind: SafeKind, val probability: Double)

/**
 * Cote décimale ESTIMÉE = 1 / probabilité (pur) : les lignes dégradées
 * (O1.5, U3.5, DC) n'ont pas de cotes scrapées — cote explicite, jamais
 * une décimale inventée (affichée comme « estimée » à l'utilisateur).
 */
fun impliedOdds(probability: Double): Double? =
    probability.takeIf { p -> p > 0.0 }?.let { 1.0 / it }

/**
 * Dégrade le meilleur pick vers sa ligne sécurisée. Null si la donnée
 * requise manque (jamais d'invention). Retourne aussi la cote estimée.
 */
fun safeDowngrade(
    best: BestPick,
    home: Double,
    draw: Double,
    away: Double,
    over15: Double?,
    over35: Double?
): SafePick? = when (best.kind) {
    MarketKind.OVER25 -> over15?.takeIf { it in 0.0..1.0 }?.let { SafePick(SafeKind.OVER15, it) }
    MarketKind.UNDER25 -> over35?.takeIf { it in 0.0..1.0 }?.let { SafePick(SafeKind.UNDER35, 1.0 - it) }
    MarketKind.BTTS_YES -> over15?.takeIf { it in 0.0..1.0 }?.let { SafePick(SafeKind.OVER15, it) }
    MarketKind.BTTS_NO -> over35?.takeIf { it in 0.0..1.0 }?.let { SafePick(SafeKind.UNDER35, 1.0 - it) }
    MarketKind.HOME_WIN -> SafePick(
        SafeKind.DC_1X,
        (home + draw).coerceIn(0.0, 1.0)
    )
    MarketKind.AWAY_WIN -> SafePick(
        SafeKind.DC_X2,
        (draw + away).coerceIn(0.0, 1.0)
    )
    MarketKind.DRAW -> SafePick(SafeKind.DC_12, (1.0 - draw).coerceIn(0.0, 1.0))
    else -> null
}

/** Borne la cote estimée aux valeurs demandées (1.5 – 5.0) pour l'affichage. */
fun oddsInRange(probability: Double, minOdds: Double = 1.5, maxOdds: Double = 5.0): Boolean {
    val o = impliedOdds(probability) ?: return false
    return o in minOdds..maxOdds
}
