package com.footpredict.local.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Thème « Premium Dark » v1.3.0 : sobre et technologique.
 * - Sombre : fond Slate 900 (#0F172A), cartes Slate 800 (#1E293B) avec
 *   bordures blanches translucides (alpha en code), accent Bleu Électrique
 *   (#3B82F6) pour validation/sélection/jauges de confiance, émeraude discret
 *   (#10B981) pour le gain, rouge doux (#F87171) pour la perte.
 * - Clair : même accent bleu (lisible sur fond blanc).
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    secondary = Color(0xFF3B82F6),
    tertiary = Color(0xFF059669)
)

private val DarkSlateColors = darkColorScheme(
    primary = Color(0xFF3B82F6),
    onPrimary = Color.White,
    secondary = Color(0xFF93C5FD),
    onSecondary = Color(0xFF0F172A),
    tertiary = Color(0xFF10B981),
    onTertiary = Color(0xFF06281A),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF273549),
    onSurfaceVariant = Color(0xFF94A3B8),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFBFDBFE),
    secondaryContainer = Color(0xFF1E3A5F),
    onSecondaryContainer = Color(0xFFBFDBFE),
    error = Color(0xFFF87171),
    onError = Color(0xFF2A0F12),
    errorContainer = Color(0xFF3B1F22),
    onErrorContainer = Color(0xFFFECACA)
)

@Composable
fun FootPredictTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    MaterialTheme(
        colorScheme = if (dark) DarkSlateColors else LightColors,
        content = content
    )
}
