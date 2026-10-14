package com.footpredict.local.presentation.screen.matchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.footpredict.local.R
import com.footpredict.local.presentation.screen.matchlist.components.AlertCard
import com.footpredict.local.presentation.screen.matchlist.components.MatchPredictionCard
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PredictionDashboardScreen(
    viewModel: MatchPredictionViewModel = hiltViewModel(),
    onOpenDetail: (Int) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.dismissNotifPrompt() }
    // NOTE : le sélecteur d’export SAF vit dans AppNav (tous onglets) :
    // le laisser ici ouvrirait DEUX sélecteurs depuis l’onglet Matchs.

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        }
    ) { padding ->
        // Tirer-vers-le-bas = recharger (même chemin que « Réessayer » :
        // cache Room si frais, resync si périmé, jamais d’invention).
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.retry() },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            when {
                uiState.isLoading && uiState.matches.isEmpty() -> {
                    // Jamais d’écran blanc : squelette + progression du calcul.
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        uiState.computeProgress?.let { (done, total) ->
                            Text(
                                "$done / $total",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                uiState.errorMessage != null && uiState.matches.isEmpty() -> {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(uiState.errorMessage ?: stringResource(R.string.error_generic))
                        Button(onClick = { viewModel.retry() }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
                else -> {
                    val topOverTitle = stringResource(R.string.top_over)
                    val topUnderTitle = stringResource(R.string.top_under)
                    val topBttsYesTitle = stringResource(R.string.top_btts_yes)
                    val topBttsNoTitle = stringResource(R.string.top_btts_no)
                    val topDcTitle = stringResource(R.string.market_dc)
                    // Onglet marché persistant (rememberSaveable) : un aller-retour
                    // vers le détail ne doit pas rabattre sur « Résultat ».
                    // C’était le bug « retour vers Résultat » depuis les onglets
                    // Buts / Double Chance / Corners (remember simple = perdu
                    // dès que l’écran quitte la composition).
                    var marketTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
                    val marketTitles = listOf(
                        stringResource(R.string.market_1x2),
                        stringResource(R.string.market_overunder),
                        stringResource(R.string.market_dc),
                        stringResource(R.string.market_btts),
                        stringResource(R.string.market_corners)
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        stringResource(R.string.live_auto_refresh),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    if (uiState.isLiveRefreshing) {
                                        Text(
                                            stringResource(R.string.live_refreshing),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Switch(
                                    checked = uiState.liveAutoRefreshEnabled,
                                    onCheckedChange = { viewModel.setLiveAutoRefresh(it) }
                                )
                            }
                        }

                        item {
                            FilterBar(
                                leagueId = uiState.leagueId,
                                query = uiState.query,
                                statusFilter = uiState.statusFilter,
                                minConfidence = uiState.minConfidence,
                                windows = uiState.windows,
                                dateRangeMs = uiState.dateRangeMs,
                                onValidate = { bundle ->
                                    viewModel.applyFilterBundle(
                                        leagueId = bundle.leagueId,
                                        query = bundle.query,
                                        statusFilter = bundle.status,
                                        minConfidence = bundle.minConfidence,
                                        dateRangeMs = bundle.rangeMs,
                                        upcomingDays = bundle.upcomingDays,
                                        pastDays = bundle.pastDays
                                    )
                                }
                            )
                        }

                        // (La tuile Performance du modèle est volontairement retirée
                        // de l’affichage ; l’évaluation tourne toujours en fond.)
                        if (uiState.showNotifPrompt) {
                            item {
                                NotifPromptCard(
                                    onEnable = {
                                        notifPermissionLauncher.launch(
                                            android.Manifest.permission.POST_NOTIFICATIONS
                                        )
                                    },
                                    onDismiss = { viewModel.dismissNotifPrompt() }
                                )
                            }
                        }

                        if (uiState.alerts.isNotEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.alerts_section),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            items(uiState.alerts, key = { it.id }) { alert ->
                                AlertCard(alert = alert)
                            }
                        }

                        // Onglets marchés (le contenu suit l’onglet actif).
                        item {
                            androidx.compose.material3.ScrollableTabRow(
                                selectedTabIndex = marketTab
                            ) {
                                marketTitles.forEachIndexed { index, title ->
                                    androidx.compose.material3.Tab(
                                        selected = marketTab == index,
                                        onClick = { marketTab = index },
                                        text = { Text(title) }
                                    )
                                }
                            }
                        }

                        // Tops = pronostics à venir : masqués en filtre « Terminés ».
                        if (marketTab == 0 && uiState.statusFilter != StatusFilter.FINISHED && uiState.topPicks.isNotEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.top_confidence),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            items(uiState.topPicks, key = { "top_${it.matchId}" }) { pick ->
                                TopPickCard(
                                    pick = pick,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        // « À éviter » : pronostics Résultat (1X2) → onglet Résultat seul.
                        if (marketTab == 0 && uiState.statusFilter != StatusFilter.FINISHED && uiState.avoidPicks.isNotEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.top_avoid),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            items(uiState.avoidPicks, key = { "avoid_${it.matchId}" }) { pick ->
                                TopPickCard(
                                    pick = pick,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    reason = if (pick.divergent) {
                                        stringResource(R.string.avoid_reason_divergence)
                                    } else {
                                        stringResource(R.string.avoid_reason_low_conf)
                                    }
                                )
                            }
                        }

                        // Tops marchés (2 max chacun, pronos à venir uniquement).
                        if (uiState.statusFilter != StatusFilter.FINISHED && marketTab == 1) {
                            MarketTopSection(title = topOverTitle, picks = uiState.topOver)
                            MarketTopSection(title = topUnderTitle, picks = uiState.topUnder)
                        }
                        if (uiState.statusFilter != StatusFilter.FINISHED && marketTab == 2) {
                            MarketTopSection(title = topDcTitle, picks = uiState.topDC)
                        }
                        if (uiState.statusFilter != StatusFilter.FINISHED && marketTab == 3) {
                            MarketTopSection(title = topBttsYesTitle, picks = uiState.topBttsYes)
                            MarketTopSection(title = topBttsNoTitle, picks = uiState.topBttsNo)
                        }
                        if (marketTab == 4) {
                            CornersSection(matches = uiState.matches)
                        }

                        if (uiState.isOffline) {
                            item {
                                Text(
                                    text = stringResource(R.string.offline_mode),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        } else if (uiState.isFromCache) {
                            item {
                                Text(
                                    text = stringResource(R.string.cached_data),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        if (!uiState.isLoading && uiState.matches.isEmpty() && uiState.errorMessage == null) {
                            item {
                                Text(
                                    text = stringResource(R.string.empty_matches),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        items(uiState.matches, key = { it.fixture.id }) { match ->
                            MatchPredictionCard(
                                enriched = match,
                                prediction = uiState.predictions[match.fixture.id],
                                liveHistory = uiState.liveHistories[match.fixture.id],
                                modifier = Modifier.fillMaxWidth(),
                                onMatchClick = {
                                    viewModel.selectMatch(it)
                                    onOpenDetail(it)
                                },
                                settled = uiState.settledMap[match.fixture.id],
                                markets = marketsForTab(marketTab),
                                showCorners = marketTab == 4
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBar(
    leagueId: Int,
    query: String,
    statusFilter: StatusFilter,
    minConfidence: Float,
    windows: FilterWindows,
    dateRangeMs: Pair<Long, Long>?,
    onValidate: (FilterBundle) -> Unit
) {
    var draftLeague by remember(leagueId) { mutableStateOf(leagueId) }
    var draftQuery by remember(query) { mutableStateOf(query) }
    var draftStatus by remember(statusFilter) { mutableStateOf(statusFilter) }
    var draftConf by remember(minConfidence) { mutableStateOf(minConfidence) }
    var draftUpcoming by remember(windows) { mutableStateOf(windows.upcomingDays.toFloat()) }
    var draftPast by remember(windows) { mutableStateOf(windows.pastDays.toFloat()) }
    var draftRange by remember(dateRangeMs) { mutableStateOf(dateRangeMs) }
    var showDates by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = leagueName(draftLeague),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.filter_league)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                MatchPredictionViewModel.LEAGUES.forEach { id ->
                    DropdownMenuItem(
                        text = { Text(leagueName(id)) },
                        onClick = {
                            expanded = false
                            draftLeague = id
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = draftQuery,
            onValueChange = { draftQuery = it },
            label = { Text(stringResource(R.string.search_teams)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusChip(StatusFilter.ALL, stringResource(R.string.filter_all), draftStatus) { draftStatus = it }
            StatusChip(StatusFilter.UPCOMING, stringResource(R.string.filter_upcoming), draftStatus) { draftStatus = it }
            StatusChip(StatusFilter.FINISHED, stringResource(R.string.filter_finished), draftStatus) { draftStatus = it }
        }

        Column {
            Text(
                stringResource(R.string.filter_min_confidence, draftConf.toInt()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = draftConf,
                onValueChange = { draftConf = it },
                valueRange = 0f..80f,
                steps = 15,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.OutlinedButton(
                onClick = { showDates = true },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    draftRange?.let { formatRange(it) }
                        ?: stringResource(R.string.filter_period_auto, windows.upcomingDays, windows.pastDays)
                )
            }
            if (draftRange != null) {
                androidx.compose.material3.TextButton(onClick = { draftRange = null }) {
                    Text(stringResource(R.string.filter_period_clear))
                }
            }
        }

        Text(
            stringResource(R.string.filter_settings_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        LabeledSlider(
            label = stringResource(R.string.filter_upcoming_days, draftUpcoming.toInt()),
            value = draftUpcoming,
            range = 1f..90f,
            onChange = { draftUpcoming = it }
        )
        LabeledSlider(
            label = stringResource(R.string.filter_past_days, draftPast.toInt()),
            value = draftPast,
            range = 1f..30f,
            onChange = { draftPast = it }
        )

        androidx.compose.material3.Button(
            onClick = {
                onValidate(
                    FilterBundle(
                        leagueId = draftLeague,
                        query = draftQuery,
                        status = draftStatus,
                        minConfidence = draftConf,
                        rangeMs = draftRange,
                        upcomingDays = draftUpcoming.toInt(),
                        pastDays = draftPast.toInt()
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.filter_validate))
        }

        androidx.compose.material3.OutlinedButton(
            onClick = {
                draftLeague = 39
                draftQuery = ""
                draftStatus = StatusFilter.ALL
                draftConf = 0f
                draftRange = null
                draftUpcoming = FilterWindows.DEFAULT_UPCOMING_DAYS.toFloat()
                draftPast = FilterWindows.DEFAULT_PAST_DAYS.toFloat()
                onValidate(
                    FilterBundle(
                        leagueId = 39,
                        query = "",
                        status = StatusFilter.ALL,
                        minConfidence = 0f,
                        rangeMs = null,
                        upcomingDays = FilterWindows.DEFAULT_UPCOMING_DAYS,
                        pastDays = FilterWindows.DEFAULT_PAST_DAYS
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.filter_reset))
        }
    }

    if (showDates) {
        val dateState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = draftRange?.first,
            initialSelectedEndDateMillis = draftRange?.second
        )
        DatePickerDialog(
            onDismissRequest = { showDates = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val s = dateState.selectedStartDateMillis
                    val e = dateState.selectedEndDateMillis
                    if (s != null && e != null) draftRange = s to maxOf(e, s)
                    showDates = false
                }) { Text(stringResource(R.string.filter_validate)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDates = false }) {
                    Text(stringResource(R.string.filter_cancel))
                }
            }
        ) {
            DateRangePicker(state = dateState)
        }
    }
}

/** Lot de filtres validés d’un coup (bouton de validation). */
data class FilterBundle(
    val leagueId: Int,
    val query: String,
    val status: StatusFilter,
    val minConfidence: Float,
    val rangeMs: Pair<Long, Long>?,
    val upcomingDays: Int,
    val pastDays: Int
)

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun formatRange(range: Pair<Long, Long>): String {
    val sdf = java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault())
    return "${sdf.format(java.util.Date(range.first))} → ${sdf.format(java.util.Date(range.second))}"
}

@Composable
private fun StatusChip(    filter: StatusFilter,
    label: String,
    selected: StatusFilter,
    onSelect: (StatusFilter) -> Unit
) {
    FilterChip(
        selected = selected == filter,
        onClick = { onSelect(filter) },
        label = { Text(label) }
    )
}

@Composable
private fun leagueName(leagueId: Int): String = when (leagueId) {
    39 -> stringResource(R.string.league_39)
    140 -> stringResource(R.string.league_140)
    135 -> stringResource(R.string.league_135)
    78 -> stringResource(R.string.league_78)
    61 -> stringResource(R.string.league_61)
    2 -> stringResource(R.string.league_2)
    else -> stringResource(R.string.league_39)
}

@Composable
/**
 * Marchés affichés par onglet (anti-mélange) : 0 = Résultat, 1 = Buts,
 * 2 = Double Chance, 3 = Les deux marquent, 4 = Corners (géré à part via
 * [showCorners], jeu vide ici). Pur et testé indirectement (aucun crash).
 */
private fun marketsForTab(tab: Int): Set<com.footpredict.local.domain.model.MarketKind> = when (tab) {
    1 -> setOf(
        com.footpredict.local.domain.model.MarketKind.OVER25,
        com.footpredict.local.domain.model.MarketKind.UNDER25
    )
    2 -> setOf(
        com.footpredict.local.domain.model.MarketKind.DC_1X,
        com.footpredict.local.domain.model.MarketKind.DC_12,
        com.footpredict.local.domain.model.MarketKind.DC_X2
    )
    3 -> setOf(
        com.footpredict.local.domain.model.MarketKind.BTTS_YES,
        com.footpredict.local.domain.model.MarketKind.BTTS_NO
    )
    4 -> emptySet()
    else -> setOf(
        com.footpredict.local.domain.model.MarketKind.HOME_WIN,
        com.footpredict.local.domain.model.MarketKind.DRAW,
        com.footpredict.local.domain.model.MarketKind.AWAY_WIN
    )
}

@Composable
private fun TopPickCard(
    pick: TopPick,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    reason: String? = null
) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${pick.homeTeam} vs ${pick.awayTeam}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                formatTopDate(pick.dateTimestamp),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                pickOutcomeLabel(pick),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            reason?.let {
                Text(it, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun formatTopDate(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp * 1000))
}

@Composable
private fun pickOutcomeLabel(pick: TopPick): String {    val pct = (pick.outcomeProb * 100).roundToInt()
    return when (pick.outcome) {
        "1" -> stringResource(R.string.confidence_pick_12, stringResource(R.string.pick_home_win, pick.homeTeam), pct)
        "2" -> stringResource(R.string.confidence_pick_12, stringResource(R.string.pick_away_win, pick.awayTeam), pct)
        "O25" -> stringResource(R.string.market_line, stringResource(R.string.over25_short), pct)
        "U25" -> stringResource(R.string.market_line, stringResource(R.string.under25_short), pct)
        "BY" -> stringResource(R.string.market_line, stringResource(R.string.btts_yes), pct)
        "BN" -> stringResource(R.string.market_line, stringResource(R.string.btts_no), pct)
        "DC1X" -> stringResource(R.string.market_line, stringResource(R.string.dc_1x, pick.homeTeam), pct)
        "DC12" -> stringResource(R.string.market_line, stringResource(R.string.dc_12, pick.homeTeam, pick.awayTeam), pct)
        "DCX2" -> stringResource(R.string.market_line, stringResource(R.string.dc_x2, pick.awayTeam), pct)
        else -> stringResource(R.string.confidence_pick_12, stringResource(R.string.pick_draw), pct)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.MarketTopSection(
    title: String,
    picks: List<TopPick>
) {
    if (picks.isEmpty()) return
    item {
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
    items(picks, key = { "mkt_${title}_${it.matchId}" }) { pick ->
        TopPickCard(
            pick = pick,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Onglet Corners (v1.3.0) : estimations « Plus/Moins de 9.5 » depuis les xG
 * ([cornersPicks], statistique pure, cotes null affichées en toutes lettres).
 * xG insuffisants → message honnête, jamais de valeur inventée.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.CornersSection(
    matches: List<com.footpredict.local.domain.model.EnrichedMatch>
) {
    item {
        Text(
            stringResource(R.string.corners_section),
            style = MaterialTheme.typography.titleMedium
        )
    }
    val picks = com.footpredict.local.domain.model.cornersPicks(matches)
    if (picks.isEmpty()) {
        item {
            Text(
                stringResource(R.string.corners_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    items(picks.take(5), key = { "corner_${it.matchId}" }) { pick ->
        androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "${pick.homeTeam} vs ${pick.awayTeam}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(
                        R.string.market_line,
                        stringResource(
                            if (pick.direction == com.footpredict.local.domain.model.CornersDirection.OVER)
                                R.string.corners_over else R.string.corners_under
                        ),
                        (pick.confidence * 100).roundToInt()
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    stringResource(
                        R.string.corners_expected,
                        "%.1f".format(pick.expectedCorners)
                    ) + " — " + stringResource(R.string.corners_pure_odds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StatsScreen(
    performance: com.footpredict.local.domain.model.PerformanceStats?,
    summary: PerformanceSummary,
    onDownload: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PerformanceSummaryCard(summary = summary)
        if (performance != null && performance.totalPredictions > 0) {
            com.footpredict.local.presentation.screen.matchlist.components.PerformanceCard(
                stats = performance,
                modifier = Modifier.fillMaxWidth()
            )
        }
        androidx.compose.material3.Button(
            onClick = onDownload,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.stats_download))
        }
    }
}

/**
 * Carte de résumé chiffré (5 lignes exigées) : totaux GAGNÉ/PERDU depuis
 * `settled_predictions`, en-cours depuis les pronostics, taux mesuré.
 * AUCUNE valeur forcée : tout vient de [summary] (buildSummary dynamique).
 * Couleur du taux : rouge < 50 %, ORANGE à l’équilibre 50–55 % (cas actuel
 * 50,0 % sur 60 résolus, export 2026-10-13), vert ≥ 55 %.
 */
@Composable
fun PerformanceSummaryCard(summary: PerformanceSummary) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryRow(stringResource(R.string.summary_total), "${summary.totalPredicted}")
            SummaryRow(stringResource(R.string.summary_won), "${summary.won}")
            SummaryRow(stringResource(R.string.summary_lost), "${summary.lost}")
            SummaryRow(stringResource(R.string.summary_pending), "${summary.pending}")
            val hitColor = when {
                summary.hitRatePct < 50.0 -> MaterialTheme.colorScheme.error
                summary.hitRatePct < 55.0 -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.tertiary
            }
            SummaryRow(
                stringResource(R.string.summary_hitrate),
                "${"%.1f".format(summary.hitRatePct)}%",
                emphasized = true,
                valueColor = hitColor
            )
        }
    }
}

@Composable
fun SummaryRow(
    label: String,
    value: String,
    emphasized: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color? = null
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
                ?: if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun NotifPromptCard(onEnable: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.notif_prompt),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button(onClick = onEnable) {
                    Text(stringResource(R.string.notif_enable))
                }
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.notif_later))
                }
            }
        }
    }
}
