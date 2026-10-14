package com.footpredict.local.res

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Garde-fou anti-crash v1.3.0 : un `%` non échappé dans une chaîne FORMATÉE
 * de `strings.xml` (ex. « 100 % hors-ligne » → `% h`) fait planter l’écran
 * avec `FormatFlagsConversionMismatchException` (constaté sur 2 téléphones,
 * 3 FATAL le 2026-10-13 sur l’écran Réglages). Ce test scanne les chaînes
 * formatées et refuse tout `%` qui n’est ni `%%` ni un format valide
 * (`%s`, `%d`, `%1$s`…). Les chaînes `formatted="false"` (jamais formatées
 * à l’exécution) sont exemptées. Exécuté sans Android (source lue en direct).
 */
class StringsFormatTest {

    @Test
    fun `no unescaped percent in formatted strings`() {
        val file = File("src/main/res/values/strings.xml")
        assertTrue("strings.xml introuvable depuis ${file.absolutePath}", file.isFile)
        val text = file.readText(Charsets.UTF_8)
        val bodies = Regex("<string name=\"[^\"]+\"(?![^>]*formatted=\"false\")[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .findAll(text)
            .map { it.groupValues[1] }
            .toList()
        assertTrue("aucune chaîne trouvée", bodies.isNotEmpty())
        val valid = Regex("%%|%\\d+\\$[a-zA-Z]|%[a-zA-Z]")
        val offenders = bodies.filter { valid.replace(it, "").contains("%") }
        assertTrue(
            "pourcent(s) non échappé(s) : $offenders",
            offenders.isEmpty()
        )
    }
}
