package com.footpredict.scrapers

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Clé universelle « HOME|AWAY|AAAA-MM-JJ » : chaque scraper résout
 * dans son référentiel (IDs incompatibles entre sites).
 */
object MatchKeys {
    fun parse(matchId: String): Triple<String, String, String>? {
        val parts = matchId.split("|")
        if (parts.size != 3) return null
        return Triple(parts[0].trim(), parts[1].trim(), parts[2].trim())
    }

    fun normalize(name: String): String =
        java.text.Normalizer.normalize(name.lowercase(), java.text.Normalizer.Form.NFD)
            .replace("[\\u0300-\\u036f]".toRegex(), "")
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

    /** Accepte AAAA-MM-JJ et JJ/MM/AA ; epoch secondes UTC, 0 si illisible. */
    fun parseDateToEpoch(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val formats = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
        )
        for (f in formats) {
            try {
                val d = LocalDate.parse(raw.trim().take(10), f)
                return d.atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond()
            } catch (_: DateTimeParseException) {
            }
        }
        return 0L
    }
}
