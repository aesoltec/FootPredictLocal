package com.footpredict.local.domain.usecase

import com.footpredict.local.domain.model.AlertSeverity
import com.footpredict.local.domain.model.AlertType
import com.footpredict.local.domain.model.BettingAlert
import com.footpredict.local.domain.model.DivergenceLevel
import com.footpredict.local.domain.model.EnrichedMatch
import com.footpredict.local.domain.model.EnsemblePrediction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * UseCase pur — génère les alertes betting à partir de l’ensemble + signaux marché.
 */
class GenerateBettingAlertsUseCase @Inject constructor() {

    suspend operator fun invoke(
        enriched: List<EnrichedMatch>,
        predictions: Map<Int, EnsemblePrediction>
    ): List<BettingAlert> = withContext(Dispatchers.Default) {
        val alerts = mutableListOf<BettingAlert>()

        enriched.forEach { em ->
            val pred = predictions[em.fixture.id] ?: return@forEach
            val home = em.fixture.homeTeamName
            val away = em.fixture.awayTeamName
            val matchId = em.fixture.id

            // 1. Bonnes affaires : notre estimation dépasse la cote affichée.
            //    Langage courant exigé : jamais « edge », « value » ni codes
            //    d’issue (HOME/DRAW/AWAY) face à l’utilisateur.
            pred.valueBets.forEach { vb ->
                val severity = when {
                    vb.edge >= 0.18 -> AlertSeverity.HIGH
                    vb.edge >= 0.12 -> AlertSeverity.MEDIUM
                    else -> AlertSeverity.LOW
                }
                val outcomeLabel = when (vb.outcome) {
                    "HOME" -> "victoire $home"
                    "AWAY" -> "victoire $away"
                    else -> "match nul"
                }
                alerts += BettingAlert(
                    id = "vb_${matchId}_${vb.outcome}",
                    matchId = matchId,
                    homeTeam = home,
                    awayTeam = away,
                    type = AlertType.VALUE_BET,
                    severity = severity,
                    title = "Bonne affaire : $outcomeLabel",
                    message = "$home vs $away — notre estimation dépasse la cote affichée de ${(vb.edge * 100).toInt()} points",
                    edge = vb.edge,
                    confidence = pred.confidence
                )
            }

            // 2. Confiance élevée + faible divergence (2 niveaux cohérents :
            //    ≥ 0.65 → "Haute", 0.55–0.65 → "Bonne". Le niveau de sévérité
            //    suit toujours l’intitulé.)
            if (pred.confidence >= 0.55 && pred.divergenceLevel == DivergenceLevel.LOW) {
                val outcomeLabel = when {
                    pred.homeWinProb >= pred.drawProb && pred.homeWinProb >= pred.awayWinProb -> "victoire $home"
                    pred.awayWinProb >= pred.drawProb -> "victoire $away"
                    else -> "match nul"
                }
                val high = pred.confidence >= 0.65
                alerts += BettingAlert(
                    id = "hc_$matchId",
                    matchId = matchId,
                    homeTeam = home,
                    awayTeam = away,
                    type = AlertType.HIGH_CONFIDENCE,
                    severity = if (high) AlertSeverity.HIGH else AlertSeverity.MEDIUM,
                    title = "${if (high) "Haute" else "Bonne"} confiance : $outcomeLabel",
                    message = "$home vs $away — confiance ${(pred.confidence * 100).toInt()} %, nos indicateurs sont d’accord",
                    confidence = pred.confidence
                )
            }

            // 3. Forte divergence (warning) — vocabulaire simple, sans jargon modèles.
            //    Sans au moins 2 modèles, aucun désaccord n’est mesurable.
            if (pred.divergenceLevel == DivergenceLevel.HIGH) {
                val modelCount = listOf(
                    pred.dixonColes != null,
                    pred.market != null,
                    pred.elo != null
                ).count { it }
                if (modelCount >= 2) {
                    alerts += BettingAlert(
                        id = "div_$matchId",
                        matchId = matchId,
                        homeTeam = home,
                        awayTeam = away,
                        type = AlertType.DIVERGENCE_WARNING,
                        severity = AlertSeverity.MEDIUM,
                        title = "Match à risque",
                        message = "$home vs $away — attention, avis partagés sur ce match",
                        confidence = pred.confidence
                    )
                }
            }
        }

        // Tri : HIGH d’abord, puis par edge/confidence.
        // Plafond anti-bruit : au-delà, les alertes MEDIUM systématiques
        // (divergence normale en début de saison) enterreraient les matchs.
        alerts.sortedWith(
            compareByDescending<BettingAlert> { it.severity }
                .thenByDescending { it.edge ?: 0.0 }
                .thenByDescending { it.confidence ?: 0.0 }
        ).take(MAX_ALERTS)
    }

    companion object {
        const val MAX_ALERTS = 12
    }
}
