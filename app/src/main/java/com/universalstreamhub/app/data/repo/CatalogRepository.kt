package com.universalstreamhub.app.data.repo

import android.content.Context
import com.universalstreamhub.app.data.bundled.BundledFreeCatalog
import com.universalstreamhub.app.data.local.UserStore
import com.universalstreamhub.app.data.remote.ApiClient
import com.universalstreamhub.app.data.remote.HubException
import com.universalstreamhub.app.data.remote.SearchResponseDto
import com.universalstreamhub.app.data.remote.TitleDetailDto
import com.universalstreamhub.app.data.remote.safeApiCall
import kotlinx.coroutines.flow.first

/**
 * Catalog access. The hub server is authoritative; when it cannot be reached
 * the verified free catalog bundled in the APK is served instead (clearly
 * labelled in the UI) so the app never shows an empty shell.
 */
class CatalogRepository(
    context: Context,
    private val store: UserStore,
    private val apiClient: ApiClient,
) {
    private val bundled = BundledFreeCatalog(context)

    private suspend fun api() = apiClient.api(store.settings.first().apiBaseUrl)

    private suspend fun region(): String = store.settings.first().region

    private fun <T> Result<T>.orBundled(build: (String?) -> T?): Result<T> {
        if (isSuccess || bundled.isEmpty) return this
        val offline = runCatching { build(exceptionOrNull()?.message) }.getOrNull() ?: return this
        return Result.success(offline)
    }

    suspend fun home() = safeApiCall { api().home(region()) }
        .orBundled { reason -> bundled.home(reason) }

    suspend fun search(
        query: String,
        type: String,
        page: Int,
        year: Int?,
        genre: String?,
        minRating: Double?,
        provider: String?,
        language: String?,
        sort: String,
    ): Result<SearchResponseDto> = safeApiCall {
        api().search(
            query = query,
            type = type,
            page = page,
            region = region(),
            year = year,
            genre = genre,
            minRating = minRating,
            provider = provider,
            language = language,
            sort = sort,
        )
    }.orBundled { reason -> bundled.search(query, type, year, genre, minRating, reason) }

    suspend fun detail(id: String): Result<TitleDetailDto> = safeApiCall { api().detail(id, region()) }
        .orBundled { bundled.detail(id) }

    suspend fun genres() = safeApiCall { api().genres() }
        .orBundled { bundled.genres() }

    suspend fun providers() = safeApiCall { api().providers(region()) }

    suspend fun openCatalog() = safeApiCall { api().openCatalog() }
        .orBundled { bundled.openCatalog() }

    suspend fun recommendations() = safeApiCall { api().recommendations() }

    suspend fun guestRecommendations(genres: List<String>) =
        safeApiCall { api().guestRecommendations(genres.joinToString(",")) }

    suspend fun downloadAuthorization(id: String) = safeApiCall { api().downloadAuthorization(id) }

    suspend fun health() = safeApiCall { api().health() }

    /** Verifies a candidate server URL before it is saved. */
    suspend fun testServer(url: String): Result<String> = safeApiCall {
        val client = apiClient.api(url)
        val health = client.health()
        if (health.status != "ok") throw HubException("That server did not report a healthy status.")
        "Connected to Universal Stream Hub API ${health.version} - TMDB ${if (health.tmdbConfigured) "configured" else "not configured"}, ${health.openCatalogSize} free titles"
    }
}
