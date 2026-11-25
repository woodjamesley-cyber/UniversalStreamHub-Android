package com.universalstreamhub.app.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class HealthDto(
    val status: String = "unknown",
    val version: String = "",
    val tmdbConfigured: Boolean = false,
    val openCatalogSize: Int = 0,
    val region: String = "US",
)

@Serializable
data class ProviderLinkDto(
    val providerId: Int? = null,
    val name: String = "",
    val packageName: String = "",
    val webUrl: String = "",
    val offlineInOwnApp: Boolean = false,
    val logoUrl: String? = null,
    val offerType: String? = null,
)

@Serializable
data class MediaSummaryDto(
    val id: String,
    val source: String = "tmdb",
    val type: String = "movie",
    val title: String = "",
    val overview: String = "",
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val releaseDate: String? = null,
    val year: Int? = null,
    val voteAverage: Double = 0.0,
    val genres: List<String> = emptyList(),
    val runtimeMinutes: Int? = null,
    val providers: List<ProviderLinkDto> = emptyList(),
    val playback: String = "PROVIDER_APP",
    val downloadable: Boolean = false,
    val license: String? = null,
    val quality: String? = null,
    val fileSizeBytes: Long? = null,
    val trailerYoutubeKey: String? = null,
) {
    val isOpen: Boolean get() = source == "open"
    val hasNativePlayback: Boolean get() = playback == "NATIVE"
}

@Serializable
data class HomeRowDto(
    val id: String = "",
    val title: String = "",
    val items: List<MediaSummaryDto> = emptyList(),
)

@Serializable
data class HomeFeedDto(
    val hero: MediaSummaryDto? = null,
    val rows: List<HomeRowDto> = emptyList(),
    val tmdbConfigured: Boolean = false,
    val openCatalogSize: Int = 0,
    val region: String = "US",
    val notice: String? = null,
)

@Serializable
data class NativeSourceDto(
    val id: String = "",
    val label: String = "",
    val url: String = "",
    val mimeType: String = "video/mp4",
    val quality: String? = null,
    val downloadAllowed: Boolean = false,
    val fileSizeBytes: Long? = null,
    val license: String = "",
)

@Serializable
data class PlaybackOptionsDto(
    val native: List<NativeSourceDto> = emptyList(),
    val providerApps: List<ProviderLinkDto> = emptyList(),
    val notes: List<String> = emptyList(),
)

@Serializable
data class CastMemberDto(
    val name: String = "",
    val character: String? = null,
    val profileUrl: String? = null,
)

@Serializable
data class CrewMemberDto(
    val name: String = "",
    val job: String = "",
)

@Serializable
data class TrailerDto(
    val name: String = "",
    val youtubeKey: String = "",
    val webUrl: String = "",
)

@Serializable
data class SeasonDto(
    val seasonNumber: Int = 0,
    val name: String = "",
    val episodeCount: Int = 0,
    val airDate: String? = null,
    val posterUrl: String? = null,
)

@Serializable
data class TitleDetailDto(
    val media: MediaSummaryDto,
    val cast: List<CastMemberDto> = emptyList(),
    val crew: List<CrewMemberDto> = emptyList(),
    val trailer: TrailerDto? = null,
    val similar: List<MediaSummaryDto> = emptyList(),
    val seasons: List<SeasonDto> = emptyList(),
    val playback: PlaybackOptionsDto = PlaybackOptionsDto(),
)

@Serializable
data class PersonDto(
    val id: Int = 0,
    val name: String = "",
    val profileUrl: String? = null,
    val knownFor: String = "Acting",
)

@Serializable
data class SearchResponseDto(
    val page: Int = 1,
    val totalPages: Int = 1,
    val totalResults: Int = 0,
    val items: List<MediaSummaryDto> = emptyList(),
    val people: List<PersonDto> = emptyList(),
    val tmdbConfigured: Boolean = false,
    val notice: String? = null,
)

@Serializable
data class GenreDto(val id: Int? = null, val name: String = "")

@Serializable
data class GenreResponseDto(val genres: List<GenreDto> = emptyList())

@Serializable
data class ProviderCatalogEntryDto(
    val id: Int? = null,
    val name: String = "",
    val packageName: String = "",
    val webUrl: String = "",
    val offlineInOwnApp: Boolean = false,
)

@Serializable
data class ProviderResponseDto(
    val providers: List<ProviderCatalogEntryDto> = emptyList(),
    val region: String = "US",
)

@Serializable
data class OpenCatalogResponseDto(
    val items: List<MediaSummaryDto> = emptyList(),
    val license: String = "",
)

@Serializable
data class PreferencesDto(
    val favoriteGenres: List<String> = emptyList(),
    val region: String = "US",
    val language: String = "en-US",
    val providerIds: List<Int> = emptyList(),
    val storageLimitGb: Int = 8,
    val wifiOnlyDownloads: Boolean = true,
    val autoplayNext: Boolean = true,
)

@Serializable
data class UserDto(
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val avatarColor: String = "#FF4D6D",
    val preferences: PreferencesDto = PreferencesDto(),
    val createdAt: Long = 0,
)

@Serializable
data class AuthResponseDto(val token: String = "", val user: UserDto = UserDto())

@Serializable
data class MeResponseDto(val user: UserDto = UserDto())

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String,
)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class ForgotPasswordRequest(val email: String)

@Serializable
data class ResetPasswordRequest(val token: String, val password: String)

@Serializable
data class SimpleMessageDto(val message: String = "", val delivered: Boolean = false)

@Serializable
data class UpdateProfileRequest(
    val displayName: String? = null,
    val avatarColor: String? = null,
    val preferences: PreferencesDto? = null,
)

@Serializable
data class LibraryItemDto(
    val id: String,
    val type: String = "movie",
    val title: String = "",
    val posterUrl: String? = null,
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    val addedAt: Long = 0,
)

@Serializable
data class ProgressItemDto(
    val id: String,
    val type: String = "movie",
    val title: String = "",
    val posterUrl: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val progress: Float = 0f,
    val updatedAt: Long = 0,
    val completed: Boolean = false,
)

@Serializable
data class HistoryItemDto(
    val id: String,
    val type: String = "movie",
    val title: String = "",
    val posterUrl: String? = null,
    val viewedAt: Long = 0,
)

@Serializable
data class LibraryResponseDto(
    val watchlist: List<LibraryItemDto> = emptyList(),
    val continueWatching: List<ProgressItemDto> = emptyList(),
    val recentlyViewed: List<HistoryItemDto> = emptyList(),
    val completed: List<ProgressItemDto> = emptyList(),
)

@Serializable
data class WatchlistRequest(
    val id: String,
    val mediaType: String,
    val title: String,
    val posterUrl: String? = null,
    val year: Int? = null,
    val genres: List<String> = emptyList(),
)

@Serializable
data class ProgressRequest(
    val id: String,
    val mediaType: String,
    val title: String,
    val posterUrl: String? = null,
    val genres: List<String> = emptyList(),
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean = false,
)

@Serializable
data class SyncRequest(
    val watchlist: List<WatchlistRequest> = emptyList(),
    val progress: List<ProgressRequest> = emptyList(),
)

@Serializable
data class RecommendationResponseDto(
    val rows: List<HomeRowDto> = emptyList(),
    val genres: List<String> = emptyList(),
    val reason: String? = null,
    val personalized: Boolean = false,
)

@Serializable
data class DownloadAuthorizationDto(
    val id: String = "",
    val title: String = "",
    val url: String = "",
    val mimeType: String = "video/mp4",
    val fileSizeBytes: Long? = null,
    val quality: String? = null,
    val license: String? = null,
    val posterUrl: String? = null,
    val downloadable: Boolean = false,
)

@Serializable
data class ApiErrorEnvelopeDto(val error: ApiErrorBodyDto? = null)

@Serializable
data class ApiErrorBodyDto(val code: String = "", val message: String = "")
