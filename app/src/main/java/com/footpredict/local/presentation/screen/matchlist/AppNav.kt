package com.footpredict.local.presentation.screen.matchlist

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.footpredict.local.R
import com.footpredict.local.presentation.screen.matchlist.components.DailyCouponScreen
import com.footpredict.local.presentation.screen.matchlist.components.FinishedScreen
import com.footpredict.local.presentation.screen.matchlist.components.MatchDetailScreen
import com.footpredict.local.presentation.screen.matchlist.components.SettingsScreen
import com.footpredict.local.presentation.theme.ThemeMode

private data class Destination(val route: String, val labelRes: Int, val glyph: String)

private val TABS = listOf(
    Destination("coupon", R.string.nav_home, "⌂"),
    Destination("matchs", R.string.nav_matches, "⚽"),
    Destination("finished", R.string.nav_finished, "☑"),
    Destination("stats", R.string.nav_stats, "Σ"),
    Destination("settings", R.string.nav_settings, "⚙")
)

/**
 * Navigation v1.3.0 : Accueil (coupon) / Matchs / Terminés / Stats / Réglages,
 * détail plein écran avec flèche retour. Un seul ViewModel partagé.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppNav(
    viewModel: MatchPredictionViewModel = hiltViewModel(),
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    // Force l'onglet Coupon au premier lancement (ignorer l'état restauré).
    LaunchedEffect(Unit) {
        if (route != "coupon" && route != null) {
            nav.navigate("coupon") { popUpTo(nav.graph.findStartDestination().id) { inclusive = true } }
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.exportMessage) {
        uiState.exportMessage?.let {
            snackbarHostState.showSnackbar(it.removePrefix("OK:").removePrefix("ERR:"))
            viewModel.clearExportMessage()
        }
    }

    // Export CSV (SAF) au niveau navigation : fonctionne depuis TOUS les
    // onglets (Stats, Terminés…). Avant, le lanceur vivait dans l’écran
    // Matchs uniquement → tap « Télécharger » sans effet depuis Stats.
    val exportPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) viewModel.writeExportToUri(uri) else viewModel.cancelExport()
    }
    LaunchedEffect(uiState.pendingExport) {
        uiState.pendingExport?.let { exportPickerLauncher.launch(it.fileName) }
    }

    Scaffold(
        topBar = {
            // Titre par vue (exigence : plus de « HB₂AO » partout).
            // Masqué sur le détail (sa propre barre nomme le match).
            if (route != "detail/{id}") {
                androidx.compose.material3.TopAppBar(
                    title = {
                        Text(
                            stringResource(
                                when (route) {
                                    "matchs" -> R.string.nav_matches
                                    "finished" -> R.string.nav_finished
                                    "stats" -> R.string.stats_title
                                    "settings" -> R.string.nav_settings
                                    else -> R.string.coupon_title
                                }
                            ),
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }
        },
        bottomBar = {
            if (route != "detail/{id}") {
                NavigationBar {
                    TABS.forEach { dest ->
                        NavigationBarItem(
                            selected = route == dest.route,
                            onClick = {
                                nav.navigate(dest.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Text(
                                    dest.glyph,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                            },
                            label = { Text(stringResource(dest.labelRes)) }
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "coupon",
            modifier = Modifier.padding(padding)
        ) {
            composable("coupon") {
                val context = androidx.compose.ui.platform.LocalContext.current
                DailyCouponScreen(
                    coupon = uiState.coupon,
                    matches = uiState.matches,
                    predictions = uiState.predictions,
                    settled = uiState.settledMap,
                    onMatchClick = { id ->
                        viewModel.selectMatch(id)
                        nav.navigate("detail/$id")
                    },
                    onShareCoupon = { text ->
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(
                            android.content.Intent.createChooser(intent, null)
                        )
                    },
                    bankrollCapital = uiState.bankrollCapital,
                    flatStake = uiState.flatStake,
                    stakingStrategy = uiState.stakingStrategy,
                    safeCoupon = uiState.safeCoupon
                )
            }
            composable("matchs") {
                PredictionDashboardScreen(
                    viewModel = viewModel,
                    onOpenDetail = { id ->
                        viewModel.selectMatch(id)
                        nav.navigate("detail/$id")
                    }
                )
            }
            composable("finished") {
                FinishedScreen(
                    matches = uiState.finishedMatches,
                    predictions = uiState.predictions,
                    settled = uiState.settledMap,
                    onMatchClick = { id ->
                        viewModel.selectMatch(id)
                        nav.navigate("detail/$id")
                    }
                )
            }
            composable("stats") {
                StatsScreen(
                    performance = uiState.performance,
                    summary = uiState.summary,
                    onDownload = { viewModel.exportHistory() }
                )
            }
            composable("settings") {
                SettingsScreen(
                    themeMode = themeMode,
                    onThemeChange = onThemeChange,
                    hitRate = uiState.performance?.hitRate ?: 0.0,
                    settledCount = uiState.performance?.totalPredictions ?: 0,
                    bankrollCapital = uiState.bankrollCapital,
                    flatStake = uiState.flatStake,
                    stakingStrategy = uiState.stakingStrategy,
                    onBankrollChange = viewModel::updateBankroll
                )
            }
            composable("detail/{id}") {
                // La sélection est nettoyée à la SORTIE de l’écran (onDispose),
                // pas dans la flèche : sinon null → auto-pop + pop = double
                // retour (le bug « retour direct à l’accueil »).
                androidx.compose.runtime.DisposableEffect(Unit) {
                    onDispose { viewModel.clearSelection() }
                }
                val detail = uiState.selectedDetail
                if (detail != null) {
                    MatchDetailScreen(
                        detail = detail,
                        onBack = { nav.popBackStack() }
                    )
                } else {
                    // Course tap → select async : indicateur bref, pas de pop
                    // (le détail n’est joignable que depuis l’app, avec sélection).
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                }
            }
        }
    }
}
