package com.footpredict.local.presentation.screen.matchlist.components

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.footpredict.local.R
import com.footpredict.local.domain.model.AlertSeverity
import com.footpredict.local.domain.model.AlertType
import com.footpredict.local.domain.model.BettingAlert

@Composable
fun AlertCard(
    alert: BettingAlert,
    modifier: Modifier = Modifier
) {
    val containerColor = when (alert.type) {
        // Confiance : bleu (couleur demandée), quel que soit le niveau.
        AlertType.HIGH_CONFIDENCE -> MaterialTheme.colorScheme.primaryContainer
        else -> when (alert.severity) {
            AlertSeverity.HIGH -> MaterialTheme.colorScheme.errorContainer
            AlertSeverity.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer
            AlertSeverity.LOW -> MaterialTheme.colorScheme.secondaryContainer
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Text(
                    text = typeLabel(alert.type),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = " · ${severityLabel(alert.severity)}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(
                text = alert.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = alert.message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp)
            )
            alert.edge?.let {
                Text(
                    text = "${stringResource(R.string.alert_value_title)} : ${"%.1f".format(it * 100)}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun severityLabel(severity: AlertSeverity): String = when (severity) {
    AlertSeverity.HIGH -> stringResource(R.string.severity_high)
    AlertSeverity.MEDIUM -> stringResource(R.string.severity_medium)
    AlertSeverity.LOW -> stringResource(R.string.severity_low)
}

@Composable
private fun typeLabel(type: AlertType): String = when (type) {
    AlertType.VALUE_BET -> stringResource(R.string.alert_type_value)
    AlertType.DROPPING_ODDS -> stringResource(R.string.alert_dropping)
    AlertType.MONEYWAY -> stringResource(R.string.alert_type_moneyway)
    // En-tête neutre : le niveau réel (Haute/Bonne) est dans le titre,
    // sinon on relit "HAUTE … · MEDIUM".
    AlertType.HIGH_CONFIDENCE -> "CONFIANCE"
    AlertType.DIVERGENCE_WARNING -> "À ÉVITER"
}
