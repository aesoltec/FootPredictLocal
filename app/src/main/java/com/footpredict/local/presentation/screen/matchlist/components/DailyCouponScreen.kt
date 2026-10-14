package com.footpredict.local.presentation.screen.matchlist.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.footpredict.local.R
import com.footpredict.local.domain.model.EnrichedMatch
import com.footpredict.local.domain.model.EnsemblePrediction
import com.footpredict.local.domain.model.SettledPrediction
import com.footpredict.local.domain.model.StakingStrategy
import com.footpredict.local.domain.model.recommendedStake
import com.footpredict.local.presentation.screen.matchlist.Coupon
import kotlin.math.roundToInt

/**
 * Accueil v1.3.0 — Coupon du Jour (premier onglet).
 *
 * - Carte premium sombre à bordure néon : Cote Totale du Combiné (produit
 *   des cotes) + Indice de Précision global (confiance moyenne).
 * - 2 à 4 [MatchPredictionCard] (logos via cache Coil + fallback initiales,
 *   jamais de crash si URL absente).
 * - Actions : « Parier sur ce coupon » (partage du coupon vers apps externes —
 *   aucune prise de pari intégrée, honnête) + « Partager ».
 * - Sécurité : jambes filtrées en amont (confiance ≥ 65 % + value bet edge > 0
 *   dans le ViewModel) ; si null → illustration épurée + message capital.
 */
@Composable
fun DailyCouponScreen(
    coupon: Coupon?,
    matches: List<EnrichedMatch>,
    predictions: Map<Int, EnsemblePrediction>,
    settled: Map<Int, SettledPrediction>,
    onMatchClick: (Int) -> Unit,
    onShareCoupon: (String) -> Unit,
    modifier: Modifier = Modifier,
    bankrollCapital: Double = 500.0,
    flatStake: Double = 10.0,
    stakingStrategy: StakingStrategy = StakingStrategy.FLAT
) {
    val byId = remember(matches) { matches.associateBy { it.fixture.id } }
    val shareText = remember(coupon) {
        if (coupon == null) "" else buildString {
            appendLine("Coupon du jour")
            coupon.legs.forEach { appendLine("• ${it.label} @ ${"%.2f".format(it.decimalOdds)}") }
            append("Cote totale : ${"%.2f".format(coupon.totalOdds)}")
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                stringResource(R.string.coupon_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        if (coupon == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "🛡️",
                            style = MaterialTheme.typography.displaySmall
                        )
                        Text(
                            stringResource(R.string.coupon_safe_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            return@LazyColumn
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        stringResource(R.string.coupon_combo_odds, "%.2f".format(coupon.totalOdds)),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(
                            R.string.coupon_precision,
                            (coupon.avgConfidence * 100).roundToInt()
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val stake = recommendedStake(
                        stakingStrategy, coupon.avgConfidence, coupon.totalOdds,
                        bankrollCapital, flatStake
                    )
                    Text(
                        stringResource(R.string.coupon_stake, "%.2f".format(stake)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        items(coupon.legs, key = { it.matchId }) { leg ->
            val em = byId[leg.matchId]
            if (em != null) {
                MatchPredictionCard(
                    enriched = em,
                    prediction = predictions[em.fixture.id],
                    modifier = Modifier.fillMaxWidth(),
                    onMatchClick = onMatchClick,
                    settled = settled[em.fixture.id]
                )
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onShareCoupon(shareText) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.coupon_bet))
                }
                OutlinedButton(
                    onClick = { onShareCoupon(shareText) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.coupon_share))
                }
            }
        }
    }
}
