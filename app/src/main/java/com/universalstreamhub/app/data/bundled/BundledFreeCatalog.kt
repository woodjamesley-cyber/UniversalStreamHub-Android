package com.universalstreamhub.app.data.bundled

import android.content.Context
import com.universalstreamhub.app.data.remote.CrewMemberDto
import com.universalstreamhub.app.data.remote.GenreDto
import com.universalstreamhub.app.data.remote.GenreResponseDto
import com.universalstreamhub.app.data.remote.HomeFeedDto
import com.universalstreamhub.app.data.remote.HomeRowDto
import com.universalstreamhub.app.data.remote.MediaSummaryDto
import com.universalstreamhub.app.data.remote.NativeSourceDto
import com.universalstreamhub.app.data.remote.OpenCatalogResponseDto
import com.universalstreamhub.app.data.remote.PlaybackOptionsDto
import com.universalstreamhub.app.data.remote.SearchResponseDto
import com.universalstreamhub.app.data.remote.TitleDetailDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BundledFreeEntry(
    val media: MediaSummaryDto,
    val source: NativeSourceDto,
    val director: String? = null,
)

@Serializable
data class BundledFreeCatalogFile(
    val generatedAt: String = "",
    val source: String = "",
    val items: List<BundledFreeEntry> = emptyList(),
)

/**
 * Read-only snapshot of public-domain films that ships inside the APK. It is only
 * used when the hub server cannot be reached, so a fresh install still offers a
 * real catalogue with real native playback instead of an empty error screen.
 *
 * Every entry was generated from live Internet Archive metadata and only kept
 * when its direct MP4 stream answered HTTP 200 and carried an explicit
 * public-domain or Creative Commons licence. See tools/generate-free-catalog.mjs.
 */
class BundledFreeCatalog(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    val entries: List<BundledFreeEntry> by lazy { load() }

    val isEmpty: Boolean get() = entries.isEmpty()

    private fun load(): List<BundledFreeEntry> = runCatching {
        appContext.assets.open(ASSET).bufferedReader().use { reader ->
            json.decodeFromString<BundledFreeCatalogFile>(reader.readText()).items
        }
    }.getOrDefault(emptyList())

    private fun notice(reason: String?): String = buildString {
        append("Showing the built-in free catalog: ")
        append(entries.size)
        append(" public-domain films streamed from the Internet Archive and playable natively.")
        if (!reason.isNullOrBlank()) {
            append(" Hub server unavailable (")
            append(reason)
            append(").")
        }
        append(" Add a hub server in Profile > Hub server for the full multi-provider catalog.")
    }

    fun home(reason: String?): HomeFeedDto {
        val all = entries.map { it.media }
        val rows = buildList {
            all.takeIf { it.isNotEmpty() }?.let { add(HomeRowDto("free-all", "Free to watch now", it)) }
            entries.map { it.media }.filter { (it.year ?: Int.MAX_VALUE) < 1970 }
                .takeIf { it.isNotEmpty() }
                ?.let { add(HomeRowDto("free-classics", "Public-domain classics", it)) }
            entries.filter { (it.source.fileSizeBytes ?: Long.MAX_VALUE) < 500L * 1024L * 1024L }
                .map { it.media }
                .takeIf { it.isNotEmpty() }
                ?.let { add(HomeRowDto("free-light", "Quick downloads", it)) }
            entries.map { it.media }.filter { media -> media.genres.any { it.equals("Comedy", true) } }
                .takeIf { it.isNotEmpty() }
                ?.let { add(HomeRowDto("free-comedy", "Comedy picks", it)) }
            entries.map { it.media }.filter { media -> media.genres.any { it.equals("Drama", true) } }
                .takeIf { it.isNotEmpty() }
                ?.let { add(HomeRowDto("free-drama", "Drama picks", it)) }
        }
        return HomeFeedDto(
            hero = all.firstOrNull(),
            rows = rows,
            tmdbConfigured = false,
            openCatalogSize = all.size,
            notice = notice(reason),
        )
    }

    fun search(
        query: String,
        type: String,
        year: Int?,
        genre: String?,
        minRating: Double?,
        reason: String?,
    ): SearchResponseDto {
        val trimmed = query.trim()
        val items = entries
            .filter { type == "all" || type == "movie" }
            .filter { entry ->
                trimmed.isEmpty() ||
                    entry.media.title.contains(trimmed, ignoreCase = true) ||
                    entry.media.overview.contains(trimmed, ignoreCase = true) ||
                    entry.media.genres.any { it.contains(trimmed, ignoreCase = true) }
            }
            .filter { year == null || it.media.year == year }
            .filter { genre == null || it.media.genres.any { candidate -> candidate.equals(genre, true) } }
            .filter { minRating == null || it.media.voteAverage >= minRating }
            .map { it.media }
        return SearchResponseDto(
            page = 1,
            totalPages = 1,
            totalResults = items.size,
            items = items,
            tmdbConfigured = false,
            notice = notice(reason),
        )
    }

    fun detail(id: String): TitleDetailDto? {
        val entry = entries.firstOrNull { it.media.id == id } ?: return null
        val similar = entries
            .filter { it.media.id != id && it.media.genres.any { genre -> entry.media.genres.any { it.equals(genre, true) } } }
            .map { it.media }
            .take(8)
        return TitleDetailDto(
            media = entry.media,
            crew = entry.director?.takeIf { it.isNotBlank() }
                ?.let { listOf(CrewMemberDto(name = it, job = "Director")) }
                ?: emptyList(),
            similar = similar,
            playback = PlaybackOptionsDto(
                native = listOf(entry.source),
                notes = listOf(
                    "Public-domain title streamed directly from the Internet Archive.",
                ),
            ),
        )
    }

    fun genres(): GenreResponseDto = GenreResponseDto(
        entries.flatMap { it.media.genres }
            .distinctBy { it.lowercase() }
            .sorted()
            .map { GenreDto(id = null, name = it) },
    )

    fun openCatalog(): OpenCatalogResponseDto = OpenCatalogResponseDto(
        items = entries.map { it.media },
        license = "Public domain / Creative Commons (per-title licence recorded in the catalog)",
    )

    private companion object {
        const val ASSET = "free_catalog.json"
    }
}
