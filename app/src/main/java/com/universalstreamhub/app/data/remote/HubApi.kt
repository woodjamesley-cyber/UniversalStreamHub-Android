package com.universalstreamhub.app.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface HubApi {

    @GET("health")
    suspend fun health(): HealthDto

    @GET("api/catalog/home")
    suspend fun home(@Query("region") region: String): HomeFeedDto

    @GET("api/catalog/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("type") type: String,
        @Query("page") page: Int,
        @Query("region") region: String,
        @Query("year") year: Int? = null,
        @Query("genre") genre: String? = null,
        @Query("minRating") minRating: Double? = null,
        @Query("provider") provider: String? = null,
        @Query("language") language: String? = null,
        @Query("sort") sort: String = "popularity.desc",
    ): SearchResponseDto

    @GET("api/catalog/genres")
    suspend fun genres(): GenreResponseDto

    @GET("api/catalog/providers")
    suspend fun providers(@Query("region") region: String): ProviderResponseDto

    @GET("api/catalog/open")
    suspend fun openCatalog(): OpenCatalogResponseDto

    @GET("api/title/{id}")
    suspend fun detail(@Path("id") id: String, @Query("region") region: String): TitleDetailDto

    @GET("api/title/{id}/playback")
    suspend fun playback(@Path("id") id: String, @Query("region") region: String): PlaybackOptionsDto

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponseDto

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponseDto

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): SimpleMessageDto

    @POST("api/auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): SimpleMessageDto

    @GET("api/me")
    suspend fun me(): MeResponseDto

    @PATCH("api/me")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): MeResponseDto

    @DELETE("api/me")
    suspend fun deleteAccount(): SimpleMessageDto

    @GET("api/library")
    suspend fun library(): LibraryResponseDto

    @POST("api/library/watchlist")
    suspend fun addToWatchlist(@Body body: WatchlistRequest): LibraryResponseDto

    @DELETE("api/library/watchlist/{id}")
    suspend fun removeFromWatchlist(@Path("id") id: String): LibraryResponseDto

    @POST("api/library/progress")
    suspend fun saveProgress(@Body body: ProgressRequest): LibraryResponseDto

    @POST("api/library/sync")
    suspend fun syncLibrary(@Body body: SyncRequest): LibraryResponseDto

    @GET("api/recommendations")
    suspend fun recommendations(): RecommendationResponseDto

    @GET("api/recommendations/guest")
    suspend fun guestRecommendations(@Query("genres") genres: String): RecommendationResponseDto

    @GET("api/downloads/{id}")
    suspend fun downloadAuthorization(@Path("id") id: String): DownloadAuthorizationDto
}
