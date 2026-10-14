package com.footpredict.local.domain.model

/**
 * Marché d’un pick : 1X2, Over/Under 2.5, BTTS ou Double Chance.
 * Le choix du meilleur pick est centralisé ici (carte + export CSV
 * utilisent la même fonction → jamais de divergence d’affichage).
 */
enum class MarketKind {
    HOME_WIN, DRAW, AWAY_WIN,
    OVER25, UNDER25,
    BTTS_YES, BTTS_NO,
    DC_1X, DC_12, DC_X2
}

/** Meilleur pick tous marchés + sa probabilité (0..1). */
data class BestPick(val kind: MarketKind, val probability: Double)

/**
 * Seuil de sécurité Over 2.5 (recalibrage 2026-10-13, export 60 résolus) :
 * « Over 2.5 » n’est proposé que si P > 0,62 strictement (ex. Liverpool 0-0
 * Fulham à 60,6 % aurait été bloqué). En dessous, le marché Over est ignoré
 * (Under reste éligible) → état honnête, jamais de pick forcé.
 */
const val OVER25_MIN_PROB = 0.62

/**
 * Meilleur pick tous marchés (pur, testé) :
 * 1X2, Over/Under (si [over25] fourni), BTTS (si [bttsYes] fourni).
 * La Double Chance est volontairement EXCLUE : couvrante par construction,
 * elle gagnerait presque toujours et masquerait les vrais signaux
 * (elle reste affichée en ligne dédiée).
 * En cas d’égalité stricte, priorité : 1X2 > Over/Under > BTTS.
 */
fun bestMarketPick(
    home: Double,
    draw: Double,
    away: Double,
    over25: Double?,
    bttsYes: Double?
): BestPick {
    var best = when {
        home >= draw && home >= away -> BestPick(MarketKind.HOME_WIN, home)
        away >= draw -> BestPick(MarketKind.AWAY_WIN, away)
        else -> BestPick(MarketKind.DRAW, draw)
    }
    fun consider(kind: MarketKind, prob: Double) {
        if (prob > best.probability) best = BestPick(kind, prob)
    }
    if (over25 != null && over25 in 0.0..1.0) {
        // Sécurité Over 2.5 : seuil strict > 0,62 (recalibrage 2026-10-13).
        if (over25 > OVER25_MIN_PROB) consider(MarketKind.OVER25, over25)
        consider(MarketKind.UNDER25, 1.0 - over25)
    }
    if (bttsYes != null && bttsYes in 0.0..1.0) {
        consider(MarketKind.BTTS_YES, bttsYes)
        consider(MarketKind.BTTS_NO, 1.0 - bttsYes)
    }
    return best
}

/**
 * Le marché proposé a-t-il gagné au score final ? (pur, testé — audit
 * externe 2026-10-14 : le statut GAGNÉ/PERDU DOIT valider le marché proposé,
 * pas seulement le 1X2. Avant, un « BTTS - No » sur 5-3 affichait GAGNÉ car
 * le 1X2 était bon : inversion logique.)
 */
fun marketHit(kind: MarketKind, goalsHome: Int, goalsAway: Int): Boolean = when (kind) {
    MarketKind.HOME_WIN -> goalsHome > goalsAway
    MarketKind.DRAW -> goalsHome == goalsAway
    MarketKind.AWAY_WIN -> goalsAway > goalsHome
    MarketKind.OVER25 -> goalsHome + goalsAway > 2
    MarketKind.UNDER25 -> goalsHome + goalsAway <= 2
    MarketKind.BTTS_YES -> goalsHome > 0 && goalsAway > 0
    MarketKind.BTTS_NO -> !(goalsHome > 0 && goalsAway > 0)
    MarketKind.DC_1X -> goalsHome >= goalsAway
    MarketKind.DC_12 -> goalsHome != goalsAway
    MarketKind.DC_X2 -> goalsAway >= goalsHome
}
