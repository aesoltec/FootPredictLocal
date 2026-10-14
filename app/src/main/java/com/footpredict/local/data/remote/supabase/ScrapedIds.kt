package com.footpredict.local.data.remote.supabase

import java.text.Normalizer

/**
 * Schéma d'IDs stable partagé avec les Edge Functions (`_shared/ids.ts`).
 *
 * Les fixtures/stats scrapées utilisent des IDs déterministes dérivés des noms
 * d'équipes, afin que `scraped_fixtures`, `team_season_stats` et `xg_stats`
 * restent cohérents sans registre central.
 */
object ScrapedIds {

    fun normalize(name: String): String =
        Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("[\\u0300-\\u036f]"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Compatible `String.hashCode()` Java et `javaStringHash` (TS). */
    fun javaHash(s: String): Int {
        var h = 0
        for (c in s) h = 31 * h + c.code
        return h
    }

    fun unifiedTeamId(teamName: String): Int = javaHash(normalize(teamName))
}
