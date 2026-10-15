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

data class SafePick(val kind: SafeKind, val probability: Double)

/**
 * Dégrade le meilleur pick vers sa ligne sécurisée. Null si la donnée
 * requise manque (jamais d’invention).
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
