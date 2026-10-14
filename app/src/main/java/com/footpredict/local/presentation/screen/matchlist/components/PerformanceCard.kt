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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.footpredict.local.R
import com.footpredict.local.domain.model.PerformanceStats

@Composable
fun PerformanceCard(
    stats: PerformanceStats,
    modifier: Modifier = Modifier
) {
    if (stats.totalPredictions == 0) return

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.performance_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                pluralStringResource(R.plurals.performance_retro, stats.totalPredictions, stats.totalPredictions),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem(
                    stringResource(R.string.performance_hitrate),
                    "${"%.1f".format(stats.hitRate * 100)}%"
                )
                StatItem(
                    stringResource(R.string.performance_n),
                    "${stats.hits}/${stats.totalPredictions}"
                )
                StatItem(
                    stringResource(R.string.performance_avgconf),
                    "${"%.0f".format(stats.avgConfidence * 100)}%"
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem(
                    stringResource(R.string.performance_highconf),
                    "${"%.1f".format(stats.highConfidenceHitRate * 100)}%"
                )
                StatItem(
                    stringResource(R.string.performance_valuebets),
                    "${"%.1f".format(stats.valueBetHitRate * 100)}%"
                )
                StatItem(
                    stringResource(R.string.performance_precision),
                    "${"%.3f".format(stats.brierScore)}"
                )
            }
            Text(
                stringResource(R.string.performance_precision_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                stringResource(
                    R.string.performance_by_outcome,
                    fmt(stats.byOutcome["HOME"]),
                    fmt(stats.byOutcome["DRAW"]),
                    fmt(stats.byOutcome["AWAY"])
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

private fun fmt(s: com.footpredict.local.domain.model.OutcomeStats?): String {
    if (s == null || s.predicted == 0) return "—"
    return "${"%.0f".format(s.hitRate * 100)}% (${s.hits}/${s.predicted})"
}
