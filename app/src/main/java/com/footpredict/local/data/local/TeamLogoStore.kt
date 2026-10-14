package com.footpredict.local.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Blasons d’équipes persistés en local, nommés par club — le mode hors-ligne
 * ne dépend plus seulement du cache Coil (évincé sous pression mémoire).
 *
 * - Fichier : `<filesDir>/team_logos/<nom-normalisé>.<ext>` (ext reprise de
 *   l’URL : svg/png, défaut svg). Le NOM du club donne le nom du fichier,
 *   comme exigé (ex. `arsenalfc.svg`).
 * - Remplissage : [ensureCachedAll] après chaque synchro réussie (repository,
 *   best-effort : un échec réseau/disque ne fait jamais échouer la synchro).
 * - Lecture : `TeamLogo` préfère le fichier local (zéro octet réseau, SVG
 *   décodé par Coil comme avant), sinon l’URL distante, sinon l’initiale.
 * - Gardes : http(s) uniquement, 512 Ko max par blason, ~40 clubs/ligue.
 */
@Singleton
class TeamLogoStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: HttpClient
) {

    companion object {
        const val DIR_NAME = "team_logos"
        const val MAX_LOGO_BYTES = 512 * 1024L
        private val NON_ALNUM = Regex("[^a-z0-9]")

        /** Nom de fichier stable depuis le nom du club (pur, testé). */
        fun fileBaseName(teamName: String): String {
            val norm = teamName.lowercase().replace(NON_ALNUM, "")
            return norm.ifBlank { "club" }
        }

        /** Extension reprise de l’URL (svg par défaut, jamais inventée au-delà). */
        fun extensionOf(logoUrl: String): String {
            val tail = logoUrl.substringAfterLast('/', "").substringBefore('?').lowercase()
            return when {
                tail.endsWith(".svg") -> "svg"
                tail.endsWith(".png") -> "png"
                tail.endsWith(".webp") -> "webp"
                tail.endsWith(".jpg") || tail.endsWith(".jpeg") -> "jpg"
                else -> "svg"
            }
        }

        /** Fichier attendu (pur, testé) : dir + nom du club + extension. */
        fun logoFileFor(dir: File, teamName: String, logoUrl: String): File =
            File(dir, "${fileBaseName(teamName)}.${extensionOf(logoUrl)}")
    }

    private val dir: File by lazy {
        File(context.filesDir, DIR_NAME).also { if (!it.isDirectory) it.mkdirs() }
    }

    /** Fichier local du blason (existant ou non — l’appelant vérifie). */
    fun logoFile(teamName: String, logoUrl: String): File = logoFileFor(dir, teamName, logoUrl)

    /** Télécharge le blason s’il manque (null si déjà là, invalide ou échec). */
    suspend fun ensureCached(teamName: String, logoUrl: String): File? = withContext(Dispatchers.IO) {
        try {
            if (!logoUrl.startsWith("http://") && !logoUrl.startsWith("https://")) return@withContext null
            val target = logoFile(teamName, logoUrl)
            if (target.isFile && target.length() > 0) return@withContext target
            val bytes = client.get(logoUrl).readBytes()
            if (bytes.isEmpty() || bytes.size > MAX_LOGO_BYTES) return@withContext null
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
            target
        } catch (_: Exception) {
            null
        }
    }

    /** Sauvegarde best-effort d’un lot (doublons par nom fusionnés). */
    suspend fun ensureCachedAll(teams: List<Pair<String, String?>>) {
        val distinct = teams
            .filter { it.second?.startsWith("http") == true }
            .distinctBy { fileBaseName(it.first) }
        for ((name, url) in distinct) {
            ensureCached(name, url ?: continue)
        }
    }
}
