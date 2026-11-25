package com.universalstreamhub.app.data.repo

import com.universalstreamhub.app.data.local.AppDatabase
import com.universalstreamhub.app.data.local.HistoryEntity
import com.universalstreamhub.app.data.local.LocalSettings
import com.universalstreamhub.app.data.local.ProgressEntity
import com.universalstreamhub.app.data.local.UserStore
import com.universalstreamhub.app.data.local.WatchlistEntity
import com.universalstreamhub.app.data.remote.ApiClient
import com.universalstreamhub.app.data.remote.LibraryResponseDto
import com.universalstreamhub.app.data.remote.MediaSummaryDto
import com.universalstreamhub.app.data.remote.ProgressRequest
import com.universalstreamhub.app.data.remote.SyncRequest
import com.universalstreamhub.app.data.remote.WatchlistRequest
import com.universalstreamhub.app.data.remote.safeApiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Single source of truth for My List / progress. Room is written first so the
 * UI works offline; when the user is signed in the same operation is mirrored
 * to the server and the library is synchronised on sign-in.
 */
class LibraryRepository(
    private val db: AppDatabase,
    private val store: UserStore,
    private val apiClient: ApiClient,
) {
    private val separator = "\u001F"

    val watchlist: Flow<List<WatchlistEntity>> = db.libraryDao().watchlist()
    val continueWatching: Flow<List<ProgressEntity>> = db.libraryDao().continueWatching()
    val history: Flow<List<HistoryEntity>> = db.libraryDao().history()

    fun isInWatchlist(id: String): Flow<Boolean> = db.libraryDao().isInWatchlist(id)

    suspend fun progressFor(id: String): ProgressEntity? = db.libraryDao().progressFor(id)

    private suspend fun signedIn(): Boolean = !store.token().isNullOrBlank()

    private suspend fun api() = apiClient.api(store.settings.first().apiBaseUrl)

    suspend fun toggleWatchlist(media: MediaSummaryDto): Boolean {
        val dao = db.libraryDao()
        val existing = dao.isInWatchlistNow(media.id)
        if (existing) {
            dao.removeFromWatchlist(media.id)
            if (signedIn()) {
                safeApiCall { api().removeFromWatchlist(media.id) }
            }
            return false
        }
        dao.addToWatchlist(media.toWatchlistEntity())
        if (signedIn()) {
            safeApiCall {
                api().addToWatchlist(
                    WatchlistRequest(
                        id = media.id,
                        mediaType = media.type,
                        title = media.title,
                        posterUrl = media.posterUrl,
                        year = media.year,
                        genres = media.genres,
                    ),
                )
            }
        }
        return true
    }

    suspend fun recordProgress(media: MediaSummaryDto, positionMs: Long, durationMs: Long, completed: Boolean = false) {
        val dao = db.libraryDao()
        val now = System.currentTimeMillis()
        val isComplete = completed || (durationMs > 0 && positionMs.toDouble() / durationMs > 0.92)
        dao.saveProgress(
            ProgressEntity(
                id = media.id,
                type = media.type,
                title = media.title,
                posterUrl = media.posterUrl,
                genres = media.genres.joinToString(separator),
                positionMs = positionMs,
                durationMs = durationMs,
                completed = isComplete,
                updatedAt = now,
            ),
        )
        dao.saveHistory(
            HistoryEntity(
                id = media.id,
                type = media.type,
                title = media.title,
                posterUrl = media.posterUrl,
                genres = media.genres.joinToString(separator),
                viewedAt = now,
            ),
        )
        if (signedIn()) {
            safeApiCall {
                api().saveProgress(
                    ProgressRequest(
                        id = media.id,
                        mediaType = media.type,
                        title = media.title,
                        posterUrl = media.posterUrl,
                        genres = media.genres,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        completed = isComplete,
                    ),
                )
            }
        }
    }

    suspend fun syncWithServer(): Result<LibraryResponseDto> = safeApiCall {
        val api = api()
        val localWatchlist = db.libraryDao().watchlist().first()
        val localProgress = db.libraryDao().allProgress()
        val response = api.syncLibrary(
            SyncRequest(
                watchlist = localWatchlist.map {
                    WatchlistRequest(it.id, it.type, it.title, it.posterUrl, it.year, it.genres.split(separator).filter(String::isNotBlank))
                },
                progress = localProgress.map {
                    ProgressRequest(
                        id = it.id,
                        mediaType = it.type,
                        title = it.title,
                        posterUrl = it.posterUrl,
                        genres = it.genres.split(separator).filter(String::isNotBlank),
                        positionMs = it.positionMs,
                        durationMs = it.durationMs,
                        completed = it.completed,
                    )
                },
            ),
        )
        cacheRemote(response)
        response
    }

    suspend fun refreshFromServer(): Result<LibraryResponseDto> = safeApiCall {
        val response = api().library()
        cacheRemote(response)
        response
    }

    private suspend fun cacheRemote(response: LibraryResponseDto) {
        val dao = db.libraryDao()
        dao.saveWatchlist(
            response.watchlist.map {
                WatchlistEntity(
                    id = it.id,
                    type = it.type,
                    title = it.title,
                    posterUrl = it.posterUrl,
                    year = it.year,
                    genres = it.genres.joinToString(separator),
                    addedAt = it.addedAt,
                )
            },
        )
        dao.saveProgress(
            (response.continueWatching + response.completed).distinctBy { it.id }.map {
                ProgressEntity(
                    id = it.id,
                    type = it.type,
                    title = it.title,
                    posterUrl = it.posterUrl,
                    genres = "",
                    positionMs = it.positionMs,
                    durationMs = it.durationMs,
                    completed = it.completed,
                    updatedAt = it.updatedAt,
                )
            },
        )
        response.recentlyViewed.forEach {
            dao.saveHistory(
                HistoryEntity(
                    id = it.id,
                    type = it.type,
                    title = it.title,
                    posterUrl = it.posterUrl,
                    genres = "",
                    viewedAt = it.viewedAt,
                ),
            )
        }
    }

    suspend fun settings(): LocalSettings = store.settings.first()

    private fun MediaSummaryDto.toWatchlistEntity(): WatchlistEntity = WatchlistEntity(
        id = id,
        type = type,
        title = title,
        posterUrl = posterUrl,
        year = year,
        genres = genres.joinToString(separator),
        addedAt = System.currentTimeMillis(),
    )
}
