package com.footpredict.local.presentation.screen.matchlist.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.footpredict.local.R
import com.footpredict.local.domain.model.DivergenceLevel
import com.footpredict.local.domain.model.EnrichedMatch
import com.footpredict.local.domain.model.EnsemblePrediction
import com.footpredict.local.domain.model.LivePredictionHistory
import com.footpredict.local.domain.model.MarketKind
import com.footpredict.local.domain.model.bestMarketPick
import com.footpredict.local.presentation.screen.matchlist.PickHelpers
import com.footpredict.local.presentation.screen.matchlist.Strength
import com.footpredict.local.presentation.screen.matchlist.strengthOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val LIVE_STATUSES = setOf("1H", "HT", "2H", "ET", "BT", "P", "LIVE", "INT")

private val StrongBlue = Color(0xFF3B82F6)
private val MediumBlue = Color(0xFF60A5FA)

/**
 * Carte de pronostic : sections de marchés (Résultat, Buts, Double Chance,
 * Les deux marquent, Corners) + proposition finale mise en valeur (badge).
 * Anti-mélange : [markets] restreint les sections affichées (un onglet =
 * un marché) ; null = tout (Terminés, Coupon). [showCorners] isole la ligne
 * Corners (onglet dédié uniquement). Le badge final et la ligne « bonne
 * affaire » n’apparaissent en mode filtré que s’ils concernent le marché
 * affiché ; statuts (live/soldé) et alerte risque restent transverses.
 * Couleurs v1.3.0 « Premium Dark » : bleu électrique pour la confiance
 * (fort > 70 %, moyen 55–70 %), neutre en dessous ; statut soldé en texte
 * simple (émeraude discret GAGNÉ, rouge doux PERDU, couleurs du thème).
 */
@Composable
fun MatchPredictionCard(
    enriched: EnrichedMatch,
    prediction: EnsemblePrediction?,
    modifier: Modifier = Modifier,
    liveHistory: LivePredictionHistory? = null,
    onMatchClick: (Int) -> Unit = {},
    /** Pronostic soldé : fond vert translucide si gagné, rouge si perdu. */
    settled: com.footpredict.local.domain.model.SettledPrediction? = null,
    markets: Set<MarketKind>? = null,
    showCorners: Boolean = true
) {
    val fixture = enriched.fixture
    val isLive = fixture.statusShort in LIVE_STATUSES
    val show1x2 = markets == null || markets.any {
        it == MarketKind.HOME_WIN || it == MarketKind.DRAW || it == MarketKind.AWAY_WIN
    }
    val showGoals = markets == null || markets.any {
        it == MarketKind.OVER25 || it == MarketKind.UNDER25
    }
    val showDc = markets == null || markets.any {
        it == MarketKind.DC_1X || it == MarketKind.DC_12 || it == MarketKind.DC_X2
    }
    val showBtts = markets == null || markets.any {
        it == MarketKind.BTTS_YES || it == MarketKind.BTTS_NO
    }

    Card(
        modifier = modifier,
        onClick = { onMatchClick(fixture.id) },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = when {
            settled != null && settled.wasHit ->
                CardDefaults.cardColors(containerColor = Color(0x3366BB6A))
            settled != null ->
                CardDefaults.cardColors(containerColor = Color(0x33E57373))
            isLive -> CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
            else -> CardDefaults.cardColors()
        }
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isLive) {
                Text(
                    text = "● LIVE ${fixture.statusShort}" +
                        if (fixture.goalsHome != null) "  ${fixture.goalsHome}-${fixture.goalsAway}" else "",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(R.string.live_rom_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TeamLogo(
                        logoUrl = fixture.homeTeamLogo,
                        teamName = fixture.homeTeamName,
                        size = 36.dp
                    )
                    Text(
                        fixture.homeTeamName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Text("vs", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    Text(
                        fixture.awayTeamName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    TeamLogo(
                        logoUrl = fixture.awayTeamLogo,
                        teamName = fixture.awayTeamName,
                        size = 36.dp
                    )
                }
            }

            if (!isLive) {
                Text(
                    formatDate(fixture.dateTimestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (fixture.goalsHome != null && fixture.goalsAway != null && !isLive) {
                Text(
                    "${fixture.goalsHome} - ${fixture.goalsAway}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                // Rappel UNIQUE sous le score, EN COLONNE : le marché au plus haut
                // % l’emporte (même fonction que l’export CSV → carte et fichier
                // disent la même chose). Les autres marchés restent détaillés
                // dans les sections (1X2, Buts, Double Chance, Corners) : UNE
                // vérité, pas trois lignes qui semblent se contredire.
                // Données FIGÉES d’avant-match, aucune réévaluation.
                if (settled != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val pick = bestMarketPick(
                            settled.homeWinProb, settled.drawProb, settled.awayWinProb,
                            settled.over25Prob, settled.bttsYesProb
                        )
                        val predictedLabel = when (pick.kind) {
                            MarketKind.HOME_WIN -> stringResource(R.string.pick_home_win, fixture.homeTeamName)
                            MarketKind.AWAY_WIN -> stringResource(R.string.pick_away_win, fixture.awayTeamName)
                            MarketKind.DRAW -> stringResource(R.string.pick_draw)
                            MarketKind.OVER25 -> stringResource(R.string.over25_short)
                            MarketKind.UNDER25 -> stringResource(R.string.under25_short)
                            MarketKind.BTTS_YES -> stringResource(R.string.btts_yes)
                            MarketKind.BTTS_NO -> stringResource(R.string.btts_no)
                            else -> stringResource(R.string.pick_home_win, fixture.homeTeamName)
                        }
                        Text(
                            stringResource(
                                R.string.settled_predicted,
                                predictedLabel,
                                (pick.probability * 100).roundToInt()
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Statut final en texte simple (v1.3.0) : émeraude discret / rouge
            // doux via les couleurs du thème, sans surcharger l’écran.
            if (settled != null) {
                Text(
                    text = stringResource(
                        if (settled.wasHit) R.string.settled_won else R.string.settled_lost
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (settled.wasHit) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.error
                )
            }

            prediction?.let { pred ->
                // Ligne Résultat : uniquement dans son onglet (ou vue complète).
                if (show1x2) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ProbItem(stringResource(R.string.prediction_home), pred.homeWinProb)
                        ProbItem(stringResource(R.string.prediction_draw), pred.drawProb)
                        ProbItem(stringResource(R.string.prediction_away), pred.awayWinProb)
                    }
                }

                // — Section Buts : côté le plus probable. —
                if (showGoals) {
                    pred.over25Prob?.let { over ->
                        val under = 1.0 - over
                        val (label, prob) = if (over >= under) {
                            stringResource(R.string.over25_short) to over
                        } else {
                            stringResource(R.string.under25_short) to under
                        }
                        MarketLine(label = label, prob = prob)
                    }
                }

                // — Section Double Chance : côté dominant + issue simple. —
                if (showDc) {
                    val dc = PickHelpers.doubleChanceOf(pred.homeWinProb, pred.drawProb, pred.awayWinProb)
                    val (dcCode, dcProb) = dc.best()
                    val dcLabel = when (dcCode) {
                        "1X" -> stringResource(R.string.dc_1x, fixture.homeTeamName)
                        "12" -> stringResource(R.string.dc_12, fixture.homeTeamName, fixture.awayTeamName)
                        else -> stringResource(R.string.dc_x2, fixture.awayTeamName)
                    }
                    MarketLine(label = stringResource(R.string.dc_section), prob = dcProb, suffix = dcLabel)
                }
                if (show1x2) {
                    val (topLabel, topProb) = listOf(
                        stringResource(R.string.pick_home_win, fixture.homeTeamName) to pred.homeWinProb,
                        stringResource(R.string.pick_draw) to pred.drawProb,
                        stringResource(R.string.pick_away_win, fixture.awayTeamName) to pred.awayWinProb
                    ).maxBy { it.second }
                    Text(
                        stringResource(R.string.pick_line, topLabel, (topProb * 100).roundToInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // — Section « Les deux marquent » : côté le plus probable. —
                if (showBtts) {
                    pred.bttsYesProb?.let { yes ->
                        val (label, prob) = if (yes >= 1.0 - yes) {
                            stringResource(R.string.btts_yes) to yes
                        } else {
                            stringResource(R.string.btts_no) to (1.0 - yes)
                        }
                        MarketLine(label = label, prob = prob)
                    }
                }

                // — Section Corners (v1.3.0) : statistique pure depuis les xG,
                // cotes null affichées en toutes lettres, jamais de 1,00 inventée.
                // Données insuffisantes → ligne absente (état honnête).
                // Visible uniquement dans son onglet (ou vue complète).
                if (showCorners) {
                    val corners = com.footpredict.local.domain.model.estimateCorners(
                        fixture.id, fixture.xgHome, fixture.xgAway
                    )
                    if (corners.direction != null) {
                        MarketLine(
                            label = stringResource(
                                if (corners.direction == com.footpredict.local.domain.model.CornersDirection.OVER)
                                    R.string.corners_over else R.string.corners_under
                            ),
                            prob = corners.confidence,
                            suffix = stringResource(R.string.corners_pure_odds)
                        )
                    }
                }

                // — Proposition finale de l’application : même fonction que l’export CSV
                // (1X2, Buts, « Les deux marquent » — la Double Chance reste une ligne dédiée).
                // En vue filtrée, affichée seulement si elle concerne le marché courant.
                val best = bestMarketPick(
                    pred.homeWinProb, pred.drawProb, pred.awayWinProb,
                    pred.over25Prob, pred.bttsYesProb
                )
                if (markets == null || best.kind in markets) {
                    val bestLabel = when (best.kind) {
                        MarketKind.HOME_WIN -> stringResource(R.string.pick_home_win, fixture.homeTeamName)
                        MarketKind.AWAY_WIN -> stringResource(R.string.pick_away_win, fixture.awayTeamName)
                        MarketKind.DRAW -> stringResource(R.string.pick_draw)
                        MarketKind.OVER25 -> stringResource(R.string.over25_short)
                        MarketKind.UNDER25 -> stringResource(R.string.under25_short)
                        MarketKind.BTTS_YES -> stringResource(R.string.btts_yes)
                        MarketKind.BTTS_NO -> stringResource(R.string.btts_no)
                        else -> stringResource(R.string.pick_home_win, fixture.homeTeamName)
                    }
                    ConfidenceBadge(label = bestLabel, prob = best.probability)
                }

                DivergenceBadge(pred.divergenceLevel)

                // « Bonne affaire » : transversale en vue complète, masquée en
                // vue filtrée pour ne pas mélanger les marchés.
                if (markets == null && pred.valueBets.isNotEmpty()) {
                    Text(
                        "${stringResource(R.string.value_bet)} : ${pred.valueBets.joinToString { it.outcome }}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            liveHistory?.let { hist ->
                if (hist.snapshots.size >= 2) {
                    val first = hist.snapshots.first()
                    val last = hist.snapshots.last()
                    val deltaHome = (last.homeWinProb - first.homeWinProb) * 100
                    val arrow = when {
                        deltaHome > 1 -> "↑"
                        deltaHome < -1 -> "↓"
                        else -> "→"
                    }
                    Text(
                        text = stringResource(
                            R.string.live_evolution,
                            arrow,
                            "%.1f".format(deltaHome)
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = androidx.compose.ui.res.pluralStringResource(
                            R.plurals.live_snapshots,
                            hist.snapshots.size,
                            hist.snapshots.size
                        ),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

/** Ligne de marché : libellé + pourcentage coloré selon sa force. */
@Composable
private fun MarketLine(label: String, prob: Double, suffix: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (suffix != null) "$label : $suffix " else "$label : ",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "${(prob * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = strengthColor(strengthOf(prob))
        )
    }
}

/** Proposition finale : badge bleu > 70 %, bleu clair 55–70 %, neutre sinon. */
@Composable
private fun ConfidenceBadge(label: String, prob: Double) {
    val strength = strengthOf(prob)
    val (container, content) = when (strength) {
        Strength.STRONG -> StrongBlue to Color.White
        Strength.MEDIUM -> MediumBlue to Color.White
        Strength.LOW -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content)
    ) {
        Text(
            text = stringResource(R.string.confidence_pick_top, label, (prob * 100).roundToInt()),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun strengthColor(strength: Strength): Color = when (strength) {
    Strength.STRONG -> StrongBlue
    Strength.MEDIUM -> MediumBlue
    Strength.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun ProbItem(label: String, value: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(
            "${"%.0f".format(value * 100)}%",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DivergenceBadge(level: DivergenceLevel) {
    // Accord normal : rien à signaler. Sinon vocabulaire simple, jamais de jargon modèles.
    if (level == DivergenceLevel.LOW) return
    val (text, color) = when (level) {
        DivergenceLevel.MEDIUM -> stringResource(R.string.divergence_medium) to MaterialTheme.colorScheme.secondary
        else -> stringResource(R.string.divergence_high) to MaterialTheme.colorScheme.error
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = color)
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp * 1000))
}
