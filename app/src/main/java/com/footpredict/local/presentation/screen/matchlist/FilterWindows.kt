package com.footpredict.local.presentation.screen.matchlist

/**
 * Fenêtres temporelles de chargement (limites par défaut strictes).
 * Persistées en SharedPreferences par le ViewModel ; modifiables dans Réglages.
 */
data class FilterWindows(
    /** Matchs à venir : au maximum ce nombre de jours (défaut 15). */
    val upcomingDays: Int = DEFAULT_UPCOMING_DAYS,
    /** Matchs terminés : au maximum ce nombre de jours (défaut 7). */
    val pastDays: Int = DEFAULT_PAST_DAYS
) {
    companion object {
        const val DEFAULT_UPCOMING_DAYS = 15
        const val DEFAULT_PAST_DAYS = 7
        const val MAX_UPCOMING_DAYS = 90
        const val MAX_PAST_DAYS = 30

        fun sanitize(upcoming: Int, past: Int) = FilterWindows(
            upcomingDays = upcoming.coerceIn(1, MAX_UPCOMING_DAYS),
            pastDays = past.coerceIn(1, MAX_PAST_DAYS)
        )
    }
}
