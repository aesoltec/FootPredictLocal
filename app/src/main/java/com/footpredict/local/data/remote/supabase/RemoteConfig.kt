package com.footpredict.local.data.remote.supabase

import com.footpredict.local.BuildConfig

/**
 * Gardes de configuration — mode zéro-clé API.
 *
 * Sans clé, les sources concernées se désactivent (liste vide / null) au lieu
 * de tirer des requêtes vouées à l'échec : l'agrégateur bascule alors sur le
 * scraping Supabase, configuré ou non.
 */
object RemoteConfig {

    fun isSupabaseConfigured(): Boolean =
        BuildConfig.SUPABASE_URL.startsWith("http://") ||
            BuildConfig.SUPABASE_URL.startsWith("https://")

    fun hasApiFootballKey(): Boolean = isRealKey(BuildConfig.API_FOOTBALL_KEY)

    fun hasFootballDataKey(): Boolean = isRealKey(BuildConfig.FOOTBALL_DATA_KEY)

    private fun isRealKey(key: String): Boolean =
        key.isNotBlank() && !key.startsWith("YOUR_")
}
