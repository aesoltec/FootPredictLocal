package com.footpredict.local.presentation.screen.matchlist.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.footpredict.local.R
import com.footpredict.local.presentation.screen.matchlist.bankrollProjection
import com.footpredict.local.presentation.screen.matchlist.settledToCardPrediction

/** Écran Terminés : cartes vert/rouge selon le résultat du pari. */
@Composable
fun FinishedScreen(
    matches: List<com.footpredict.local.domain.model.EnrichedMatch>,
    predictions: Map<Int, com.footpredict.local.domain.model.EnsemblePrediction>,
    settled: Map<Int, com.footpredict.local.domain.model.SettledPrediction>,
    onMatchClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(matches, key = { it.fixture.id }) { match ->
            // Terminés : pas de prédiction « en direct » (match fini) → on
            // rejoue les probas mémorisées avant match (Over/BTTS/DC/Corners
            // inclus, mêmes chiffres que l’export CSV).
            MatchPredictionCard(
                enriched = match,
                prediction = predictions[match.fixture.id]
                    ?: settled[match.fixture.id]?.let(::settledToCardPrediction),
                modifier = Modifier.fillMaxWidth(),
                onMatchClick = onMatchClick,
                settled = settled[match.fixture.id]
            )
        }
    }
}

/** Écran Réglages : thème, bankroll, notifications, à propos. */
@Composable
fun SettingsScreen(
    themeMode: com.footpredict.local.presentation.theme.ThemeMode,
    onThemeChange: (com.footpredict.local.presentation.theme.ThemeMode) -> Unit,
    hitRate: Double,
    settledCount: Int,
    modifier: Modifier = Modifier,
    bankrollCapital: Double = com.footpredict.local.domain.model.DEFAULT_BANKROLL,
    flatStake: Double = com.footpredict.local.domain.model.FLAT_DEFAULT_STAKE,
    stakingStrategy: com.footpredict.local.domain.model.StakingStrategy =
        com.footpredict.local.domain.model.StakingStrategy.FLAT,
    onBankrollChange: (Double, Double, com.footpredict.local.domain.model.StakingStrategy) -> Unit =
        { _, _, _ -> }
) {
    var stake by androidx.compose.runtime.remember(flatStake) {
        androidx.compose.runtime.mutableStateOf(
            if (flatStake % 1.0 == 0.0) "${flatStake.toLong()}" else "$flatStake"
        )
    }
    var avgOdd by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("1.90") }
    var capital by androidx.compose.runtime.remember(bankrollCapital) {
        androidx.compose.runtime.mutableStateOf(
            if (bankrollCapital % 1.0 == 0.0) "${bankrollCapital.toLong()}" else "$bankrollCapital"
        )
    }
    var strategy by androidx.compose.runtime.remember(stakingStrategy) {
        androidx.compose.runtime.mutableStateOf(stakingStrategy)
    }
    fun pushBankroll() {
        onBankrollChange(
            capital.replace(',', '.').toDoubleOrNull() ?: bankrollCapital,
            stake.replace(',', '.').toDoubleOrNull() ?: flatStake,
            strategy
        )
    }
    val (profit, roi) = remember(stake, avgOdd, hitRate, settledCount) {
        bankrollProjection(
            hitRate,
            settledCount,
            stake.replace(',', '.').toDoubleOrNull() ?: 0.0,
            avgOdd.replace(',', '.').toDoubleOrNull() ?: 0.0
        )
    }
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
        }
        item {
            Column {
                com.footpredict.local.presentation.theme.ThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = themeMode == mode,
                            onClick = { onThemeChange(mode) }
                        )
                        Text(
                            when (mode) {
                                com.footpredict.local.presentation.theme.ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                                com.footpredict.local.presentation.theme.ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                                com.footpredict.local.presentation.theme.ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                            }
                        )
                    }
                }
            }
        }
        item {
            Text(stringResource(R.string.settings_bankroll), style = MaterialTheme.typography.titleMedium)
        }
        item {
            androidx.compose.material3.OutlinedTextField(
                value = capital,
                onValueChange = { capital = it; pushBankroll() },
                label = { Text(stringResource(R.string.settings_capital)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Text(
                stringResource(R.string.settings_staking_title),
                style = MaterialTheme.typography.titleSmall
            )
        }
        item {
            Column {
                listOf(
                    com.footpredict.local.domain.model.StakingStrategy.FLAT to
                        stringResource(R.string.settings_staking_flat),
                    com.footpredict.local.domain.model.StakingStrategy.KELLY to
                        stringResource(R.string.settings_staking_kelly)
                ).forEach { (mode, label) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = strategy == mode,
                            onClick = { strategy = mode; pushBankroll() }
                        )
                        Text(label)
                    }
                }
            }
        }
        item {
            androidx.compose.material3.OutlinedTextField(
                value = stake,
                onValueChange = { stake = it; pushBankroll() },
                label = { Text(stringResource(R.string.settings_stake)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            androidx.compose.material3.OutlinedTextField(
                value = avgOdd,
                onValueChange = { avgOdd = it },
                label = { Text(stringResource(R.string.settings_avgodd)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Text(
                stringResource(
                    R.string.settings_bankroll_result,
                    "%.2f".format(profit).replace('.', ','),
                    "%.1f".format(roi).replace('.', ',')
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        item {
            Text(
                stringResource(R.string.settings_bankroll_hint),
                style = MaterialTheme.typography.labelSmall
            )
        }
        item {
            Text(
                stringResource(R.string.settings_notifications),
                style = MaterialTheme.typography.labelSmall
            )
        }
        item {
            Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium)
        }
        item {
            Text(
                stringResource(R.string.settings_about_text, com.footpredict.local.BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
