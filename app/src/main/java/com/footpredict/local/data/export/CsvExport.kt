package com.footpredict.local.data.export

/**
 * Ligne d’historique complet — colonnes DANS L’ORDRE imposé par le cahier
 * des charges (calculs et corrections de formules futures).
 *
 * Colonne `simulation_gain_10e` (v1.3.0, ajoutée EN FIN pour ne jamais
 * réordonner l’existant) : résultat comptable pré-calculé d’une mise plate
 * de 10 € (le CSV brut ne porte pas de formules dynamiques) — GAGNÉ :
 * `cote × 10 − 10`, PERDU : `−10`, EN_ATTENTE : vide (jamais inventé).
 * Dans Excel/Sheets : cumul de la colonne = courbe de rentabilité.
 */
data class HistoryRow(
    val matchId: String,
    val matchDate: String,
    val competitionName: String,
    val homeTeam: String,
    val awayTeam: String,
    val finalScoreHome: Int?,
    val finalScoreAway: Int?,
    val appMarketProposal: String,
    val appConfidencePct: Double,
    val bookmakerOdds: Double?,
    val bookmakerImpliedPct: Double?,
    val valueBetEdgePct: Double?,
    val totalXgHome: Double?,
    val totalXgAway: Double?,
    val predictionStatus: String,
    val predictionErrorMargin: Double?,
    val simulationGain10e: Double? = null
) {
    companion object {
        const val STATUS_WON = "GAGNÉ"
        const val STATUS_LOST = "PERDU"
        const val STATUS_PENDING = "EN_ATTENTE"

        val HEADER = listOf(
            "match_id", "match_date", "competition_name",
            "home_team", "away_team",
            "final_score_home", "final_score_away",
            "app_market_proposal", "app_confidence_pct",
            "bookmaker_odds", "bookmaker_implied_pct",
            "value_bet_edge_pct",
            "total_xg_home", "total_xg_away",
            "prediction_status", "prediction_error_margin",
            "simulation_gain_10e"
        )
    }
}

private fun cell(v: Any?): String {
    val s = when (v) {
        null -> return ""
        // Locale US imposée : le CSV utilise toujours le point décimal.
        is Double -> if (v.isNaN()) return "" else "%.2f".format(java.util.Locale.US, v)
        else -> v.toString()
    }
    return if (s.contains(',') || s.contains('"') || s.contains('\n')) {
        "\"" + s.replace("\"", "\"\"") + "\""
    } else s
}

/** CSV RFC 4180 minimal (séparateur virgule, CRLF), en-tête imposé en premier. */
fun historyToCsv(rows: List<HistoryRow>): String {
    val sb = StringBuilder()
    sb.append(HistoryRow.HEADER.joinToString(",")).append("\r\n")
    for (r in rows) {
        sb.append(
            listOf(
                r.matchId, r.matchDate, r.competitionName,
                r.homeTeam, r.awayTeam,
                r.finalScoreHome, r.finalScoreAway,
                r.appMarketProposal, r.appConfidencePct,
                r.bookmakerOdds, r.bookmakerImpliedPct,
                r.valueBetEdgePct,
                r.totalXgHome, r.totalXgAway,
                r.predictionStatus, r.predictionErrorMargin,
                r.simulationGain10e
            ).joinToString(",") { cell(it) }
        ).append("\r\n")
    }
    return sb.toString()
}
