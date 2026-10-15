package com.footpredict.local.presentation.screen.matchlist.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.footpredict.local.data.local.TeamLogoStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Accès au magasin de blasons depuis les composables (sans ViewModel). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TeamLogoStoreEntryPoint {
    fun teamLogoStore(): TeamLogoStore
}

/**
 * Blason d’équipe avec cache agressif Coil (mémoire + disque 512 Mo) :
 * 0 ms au réaffichage, sans appel réseau après la première synchro.
 * Fallback anti-crash : cercle coloré (couleur dérivée du nom, stable)
 * avec l’initiale du club si hors-ligne sans cache ou URL corrompue.
 */
@Composable
fun TeamLogo(
    logoUrl: String?,
    teamName: String,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    val context = LocalContext.current
    // Hors-ligne durable : le fichier `<nom-du-club>.<ext>` (sauvegardé à la
    // synchro) passe avant l’URL distante ; l’URL ne sert qu’en 1er chargement.
    val store = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            TeamLogoStoreEntryPoint::class.java
        ).teamLogoStore()
    }
    val localFile = remember(teamName, logoUrl) {
        logoUrl?.takeIf { it.isNotBlank() }
            ?.let { store.logoFile(teamName, it).takeIf { f -> f.isFile && f.length() > 0 } }
    }
    if (logoUrl.isNullOrBlank() && localFile == null) {
        InitialsAvatar(teamName = teamName, modifier = modifier, size = size)
        return
    }
    val data = localFile ?: logoUrl
    // Audit OOM 2026-10-14 (X658E, heap 128 Mo) : décodage borné à 128 px
    // (affiché 28–36 dp) — un SVG fournisseur en pleine taille native coûtait
    // ~10 Mo à lui seul. Le fichier local reste intact ; hors-ligne inchangé.
    SubcomposeAsyncImage(
        model = remember(data) {
            ImageRequest.Builder(context)
                .data(data)
                .memoryCacheKey(data.toString())
                .diskCacheKey(data.toString())
                .size(128)
                .crossfade(true)
                .build()
        },
        contentDescription = teamName,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
        error = { InitialsAvatar(teamName = teamName, size = size) },
        loading = { InitialsAvatar(teamName = teamName, size = size) }
    )
}

/** Cercle d’initiale, couleur stable dérivée du nom (pas d’aléatoire). */
@Composable
fun InitialsAvatar(
    teamName: String,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    val color = remember(teamName) { clubColor(teamName) }
    val initial = remember(teamName) {
        teamName.trim().firstOrNull()?.uppercase() ?: "?"
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initial,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

private val CLUB_COLORS = listOf(
    Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFFEF6C00),
    Color(0xFF6A1B9A), Color(0xFFC62828), Color(0xFF00838F),
    Color(0xFF4E342E), Color(0xFF37474F)
)

/** Couleur stable par club (hash du nom normalisé). */
fun clubColor(teamName: String): Color {
    val n = teamName.lowercase().replace(Regex("[^a-z0-9]"), "")
    var h = 0
    for (c in n) h = 31 * h + c.code
    return CLUB_COLORS[kotlin.math.abs(h) % CLUB_COLORS.size]
}
