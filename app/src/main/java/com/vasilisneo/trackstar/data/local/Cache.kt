package com.vasilisneo.trackstar.data.local

import com.google.gson.reflect.TypeToken
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.AuthTokenHolder

// Wraps a repository fetch with the generic JSON cache: on success, refresh the cache under
// `cacheKey`; on an *offline* failure, return the last cached value if present (otherwise the error
// stands). Real server/business errors (offline == false) are never masked with stale data.
//
// Usage in a repository:
//   suspend fun getProfile() = cachedRead("profile") { apiCall { api.getProfile() } }
//
// The block must preserve the offline flag on errors (return the ApiResult.Error unchanged rather
// than rebuilding it), so mapping wrappers should do `is ApiResult.Error -> r`.
suspend inline fun <reified T> cachedRead(
    cacheKey: String,
    fetch: suspend () -> ApiResult<T>,
): ApiResult<T> {
    val userId = AuthTokenHolder.userId
    return when (val result = fetch()) {
        is ApiResult.Success -> {
            if (userId != null && LocalStore.isReady) {
                runCatching {
                    LocalStore.db.cacheDao().put(
                        CacheEntry(userId, cacheKey, LocalStore.gson.toJson(result.data)),
                    )
                }
            }
            result
        }
        is ApiResult.Error -> {
            if (result.offline && userId != null && LocalStore.isReady) {
                val cached = runCatching {
                    LocalStore.db.cacheDao().get(userId, cacheKey)?.let { json ->
                        LocalStore.gson.fromJson<T>(json, object : TypeToken<T>() {}.type)
                    }
                }.getOrNull()
                if (cached != null) return ApiResult.Success(cached)
            }
            result
        }
    }
}
