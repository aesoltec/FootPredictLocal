package com.footpredict.local.presentation.screen.matchlist.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.footpredict.local.R
import com.footpredict.local.presentation.screen.matchlist.H2HStats
import com.footpredict.local.presentation.screen.matchlist.MatchDetail
import com.footpredict.local.presentation.screen.matchlist.OddsPoint
import kotlin.math.roundToInt

private val Emerald = Color(0xFF00A86B)
private val SignalRed = Color(0xFFD32F2F)
private val Slate = Color(0xFF64748B)
private val HomeBlue = Color(0xFF1565C0)

/**
 * Écran de détail d’un match : 4 graphiques Canvas animés + rappels.
 * Toute section sans données affiche un état vide honnête, jamais de zéros inventés.
 * Flèche retour systématique (spec v1.3.0).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailScreen(
    detail: MatchDetail,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "${detail.match.fixture.homeTeamName} vs ${detail.match.fixture.awayTeamName}",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                val pred = detail.prediction
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TeamLogo(
                            logoUrl = detail.match.fixture.homeTeamLogo,
                            teamName = detail.match.fixture.homeTeamName,
                            size = 36.dp
                        )
                        Text(
                            "${detail.match.fixture.homeTeamName} vs ${detail.match.fixture.awayTeamName}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TeamLogo(
                            logoUrl = detail.match.fixture.awayTeamLogo,
                            teamName = detail.match.fixture.awayTeamName,
                            size = 36.dp
                        )
                    }
                    if (pred != null) {
                        Text(
                            stringResource(
                                R.string.confidence_pick_top,
                                topPickLabel(detail),
                                (maxOf(pred.homeWinProb, pred.drawProb, pred.awayWinProb) * 100).roundToInt()
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            item {
                SectionTitle(stringResource(R.string.detail_value_title))
                val appProb = detail.prediction?.let {
                    maxOf(it.homeWinProb, it.drawProb, it.awayWinProb)
                }
                if (appProb != null) {
                    ValueBetComparisonGraph(
                        appProb = appProb,
                        marketProb = detail.marketPickProb,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    EmptyChart()
                }
            }

            item {
                SectionTitle(stringResource(R.string.detail_xg_title))
                if (detail.xgHomeTrend.size >= 2 && detail.xgAwayTrend.size >= 2) {
                    XgTrendGraph(
                        home = detail.xgHomeTrend,
                        away = detail.xgAwayTrend,
                        homeName = detail.match.fixture.homeTeamName,
                        awayName = detail.match.fixture.awayTeamName,
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    )
                } else {
                    EmptyChart()
                }
            }

            item {
                SectionTitle(stringResource(R.string.detail_h2h_title))
                if (detail.h2h.total > 0) {
                    H2HBars(
                        h2h = detail.h2h,
                        homeName = detail.match.fixture.homeTeamName,
                        awayName = detail.match.fixture.awayTeamName,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    EmptyChart()
                }
            }

            item {
                val arrow = trendArrow(detail.oddsHistory.mapNotNull { it.homeProb })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(stringResource(R.string.detail_odds_title))
                    if (arrow != null) {
                        Text(
                            " $arrow",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                if (detail.oddsHistory.size >= 2) {
                    OddsMovementGraph(
                        points = detail.oddsHistory,
                        modifier = Modifier.fillMaxWidth().height(160.dp)
                    )
                } else {
                    EmptyChart()
                }
            }
        }
    }
}

@Composable
private fun topPickLabel(detail: MatchDetail): String {
    val pred = detail.prediction ?: return ""
    val f = detail.match.fixture
    return when {
        pred.homeWinProb >= pred.drawProb && pred.homeWinProb >= pred.awayWinProb ->
            stringResource(R.string.pick_home_win, f.homeTeamName)
        pred.awayWinProb >= pred.drawProb ->
            stringResource(R.string.pick_away_win, f.awayTeamName)
        else -> stringResource(R.string.pick_draw)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

/**
 * Flèche de tendance du marché : compare les deux dernières valeurs.
 * Pure via [marketTrend] (testée).
 */
private fun trendArrow(values: List<Double>): String? =
    marketTrend(values)?.let { if (it) "↑" else "↓" }

/** Vrai si le marché monte (dernière > avant-dernière), null si indécidable. */
fun marketTrend(values: List<Double>): Boolean? {
    if (values.size < 2) return null
    val last = values.last()
    val prev = values[values.size - 2]
    if (last == prev) return null
    return last > prev
}

@Composable
private fun EmptyChart() {
    Text(
        stringResource(R.string.detail_insufficient),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Donut comparatif : anneau externe = % app (vert si écart ≥ 0, rouge sinon),
 * anneau interne fin = % bookmaker, centre = écart net + AVANTAGE/SOUS-COTÉ.
 */
@Composable
fun ValueBetComparisonGraph(
    appProb: Double,
    marketProb: Double?,
    modifier: Modifier = Modifier
) {
    var target by remember { mutableStateOf(0f) }
    val progress by animateFloatAsState(target, tween(900), label = "donut")
    LaunchedEffect(Unit) { target = 1f }

    val edge = if (marketProb != null) appProb - marketProb else null
    val mainColor = if ((edge ?: 0.0) >= 0) Emerald else SignalRed

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(180.dp)) {
                val outerW = 22.dp.toPx()
                val sweep = { p: Double -> (360f * p.toFloat() * progress) }
                // Fond + anneau app (externe).
                drawArc(
                    color = Color.LightGray.copy(alpha = 0.3f),
                    startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    style = Stroke(outerW)
                )
                drawArc(
                    color = mainColor,
                    startAngle = -90f, sweepAngle = sweep(appProb.coerceIn(0.0, 1.0)),
                    useCenter = false, style = Stroke(outerW, cap = StrokeCap.Round)
                )
                // Anneau bookmaker (interne, fin, ardoise).
                if (marketProb != null) {
                    val inset = 30.dp.toPx()
                    drawArc(
                        color = Slate.copy(alpha = 0.35f),
                        startAngle = -90f, sweepAngle = 360f, useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        style = Stroke(8.dp.toPx())
                    )
                    drawArc(
                        color = Slate,
                        startAngle = -90f, sweepAngle = sweep(marketProb.coerceIn(0.0, 1.0)),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        style = Stroke(8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (edge != null) {
                    val sign = if (edge >= 0) "+" else ""
                    Text(
                        "$sign${"%.1f".format(edge * 100)}%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = mainColor
                    )
                    Text(
                        stringResource(
                            if (edge >= 0) R.string.detail_advantage else R.string.detail_under
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        "${(appProb * 100).roundToInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.detail_market_unavailable),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        if (marketProb != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(mainColor, stringResource(R.string.detail_legend_app, (appProb * 100).roundToInt()))
                LegendDot(Slate, stringResource(R.string.detail_legend_market, (marketProb * 100).roundToInt()))
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(10.dp)) { drawCircle(color) }
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * Tendance xG : deux lignes brisées (domicile bleu, extérieur rouge),
 * 5 derniers matchs de chaque équipe (M-5..M-1).
 */
@Composable
fun XgTrendGraph(
    home: List<Double>,
    away: List<Double>,
    homeName: String,
    awayName: String,
    modifier: Modifier = Modifier
) {
    var target by remember { mutableStateOf(0f) }
    val progress by animateFloatAsState(target, tween(900), label = "xg")
    LaunchedEffect(Unit) { target = 1f }

    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(HomeBlue, homeName)
            LegendDot(SignalRed, awayName)
        }
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val maxY = (maxOf(home.maxOrNull() ?: 0.0, away.maxOrNull() ?: 0.0, 1.0)).toFloat()
            val n = maxOf(home.size, away.size).coerceAtLeast(2)
            fun point(i: Int, v: Double): Offset {
                val x = size.width * i / (n - 1)
                val y = size.height - (size.height - 8.dp.toPx()) * (v.toFloat() / maxY) - 4.dp.toPx()
                return Offset(x.coerceIn(0f, size.width), y.coerceIn(0f, size.height))
            }
            fun pathOf(values: List<Double>): Path {
                val p = Path()
                values.forEachIndexed { i, v ->
                    val pt = point(i, v)
                    if (i == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
                }
                return p
            }
            // Coupe l’animation par progression : on dessine partiellement via segments.
            drawPath(pathOf(home), HomeBlue, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round), alpha = progress)
            drawPath(pathOf(away), SignalRed, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round), alpha = progress)
            home.forEachIndexed { i, v -> drawCircle(HomeBlue, 4.dp.toPx(), point(i, v)) }
            away.forEachIndexed { i, v -> drawCircle(SignalRed, 4.dp.toPx(), point(i, v)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            repeat(maxOf(home.size, away.size)) { i ->
                Text("M-${maxOf(home.size, away.size) - i}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * H2H : 3 barres horizontales (victoires A / nuls / victoires B) avec effectifs.
 */
@Composable
fun H2HBars(
    h2h: H2HStats,
    homeName: String,
    awayName: String,
    modifier: Modifier = Modifier
) {
    var target by remember { mutableStateOf(0f) }
    val progress by animateFloatAsState(target, tween(900), label = "h2h")
    LaunchedEffect(Unit) { target = 1f }
    val total = h2h.total.coerceAtLeast(1).toFloat()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        H2HRow(homeName, h2h.homeWins, h2h.homeWins / total * progress, HomeBlue)
        H2HRow(
            stringResource(R.string.prediction_draw), h2h.draws, h2h.draws / total * progress, Slate
        )
        H2HRow(awayName, h2h.awayWins, h2h.awayWins / total * progress, SignalRed)
        Text(
            stringResource(R.string.detail_h2h_total, h2h.total),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun H2HRow(label: String, count: Int, fraction: Float, color: Color) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("$count", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            drawRoundRect(
                Color.LightGray.copy(alpha = 0.3f),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
            drawRoundRect(
                color,
                topLeft = Offset.Zero,
                size = Size(size.width * fraction.coerceIn(0f, 1f), size.height),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
        }
    }
}

/**
 * Mouvement des cotes : probabilité implicite domicile dans le temps.
 * < 2 points → état vide (la courbe se construit au fil des synchros).
 */
@Composable
fun OddsMovementGraph(
    points: List<OddsPoint>,
    modifier: Modifier = Modifier
) {
    var target by remember { mutableStateOf(0f) }
    val progress by animateFloatAsState(target, tween(900), label = "odds")
    LaunchedEffect(Unit) { target = 1f }
    val values = points.mapNotNull { it.homeProb }
    if (values.size < 2) {
        Text(
            stringResource(R.string.detail_insufficient),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val min = values.minOrNull() ?: 0.0
            val max = values.maxOrNull() ?: 1.0
            val span = (max - min).takeIf { it > 0.001 } ?: 1.0
            fun point(i: Int, v: Double): Offset {
                val x = size.width * i / (values.size - 1)
                val y = size.height - 8.dp.toPx() -
                    (size.height - 16.dp.toPx()) * ((v - min) / span).toFloat()
                return Offset(x, y.coerceIn(0f, size.height))
            }
            val path = Path()
            values.forEachIndexed { i, v ->
                val pt = point(i, v)
                if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
            }
            drawPath(path, HomeBlue, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round), alpha = progress)
            values.forEachIndexed { i, v -> drawCircle(HomeBlue, 4.dp.toPx(), point(i, v)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${((values.minOrNull() ?: 0.0) * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                "${((values.maxOrNull() ?: 0.0) * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
