package com.footpredict.local.domain.model

/**
 * Wrapper de résultat unifié pour les opérations asynchrones (offline-first).
 */
sealed interface Resource<out T> {
    data object Loading : Resource<Nothing>
    data class Success<T>(val data: T, val fromCache: Boolean) : Resource<T>
    data class Error(val message: String, val cached: Any? = null) : Resource<Nothing>
}
