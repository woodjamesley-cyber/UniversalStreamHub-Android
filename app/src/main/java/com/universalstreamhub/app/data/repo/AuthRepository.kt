package com.universalstreamhub.app.data.repo

import com.universalstreamhub.app.data.local.LocalSettings
import com.universalstreamhub.app.data.local.UserStore
import com.universalstreamhub.app.data.remote.ApiClient
import com.universalstreamhub.app.data.remote.ForgotPasswordRequest
import com.universalstreamhub.app.data.remote.HubException
import com.universalstreamhub.app.data.remote.LoginRequest
import com.universalstreamhub.app.data.remote.PreferencesDto
import com.universalstreamhub.app.data.remote.RegisterRequest
import com.universalstreamhub.app.data.remote.ResetPasswordRequest
import com.universalstreamhub.app.data.remote.SimpleMessageDto
import com.universalstreamhub.app.data.remote.UpdateProfileRequest
import com.universalstreamhub.app.data.remote.UserDto
import com.universalstreamhub.app.data.remote.safeApiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class AuthRepository(
    private val store: UserStore,
    private val apiClient: ApiClient,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    val signedIn: Flow<Boolean> = store.userJson.map { !it.isNullOrBlank() }

    val user: Flow<UserDto?> = store.userJson.map { raw ->
        raw?.let {
            try {
                json.decodeFromString(UserDto.serializer(), it)
            } catch (_: Exception) {
                null
            }
        }
    }

    val settings: Flow<LocalSettings> = store.settings

    val session: Flow<Pair<UserDto?, LocalSettings>> = combine(user, store.settings) { user, settings -> user to settings }

    private suspend fun api(): com.universalstreamhub.app.data.remote.HubApi {
        return apiClient.api(store.settings.first().apiBaseUrl)
    }

    suspend fun login(email: String, password: String): Result<UserDto> = safeApiCall {
        val response = api().login(LoginRequest(email.trim(), password))
        if (response.token.isBlank()) throw HubException("The server did not return a session token.")
        store.saveSession(response.token, json.encodeToString(UserDto.serializer(), response.user))
        store.syncPreferencesToLocal(response.user)
        response.user
    }

    suspend fun register(email: String, password: String, displayName: String): Result<UserDto> = safeApiCall {
        val response = api().register(RegisterRequest(email.trim(), password, displayName.trim()))
        if (response.token.isBlank()) throw HubException("The server did not return a session token.")
        store.saveSession(response.token, json.encodeToString(UserDto.serializer(), response.user))
        store.syncPreferencesToLocal(response.user)
        response.user
    }

    suspend fun forgotPassword(email: String): Result<SimpleMessageDto> = safeApiCall {
        api().forgotPassword(ForgotPasswordRequest(email.trim()))
    }

    suspend fun resetPassword(token: String, password: String): Result<SimpleMessageDto> = safeApiCall {
        api().resetPassword(ResetPasswordRequest(token.trim(), password))
    }

    suspend fun refreshProfile(): Result<UserDto> = safeApiCall {
        val response = api().me()
        store.updateUserJson(json.encodeToString(UserDto.serializer(), response.user))
        store.syncPreferencesToLocal(response.user)
        response.user
    }

    suspend fun updateProfile(displayName: String?, avatarColor: String?, preferences: PreferencesDto?): Result<UserDto> =
        safeApiCall {
            val response = api().updateProfile(UpdateProfileRequest(displayName, avatarColor, preferences))
            store.updateUserJson(json.encodeToString(UserDto.serializer(), response.user))
            store.syncPreferencesToLocal(response.user)
            response.user
        }

    suspend fun deleteAccount(): Result<SimpleMessageDto> = safeApiCall {
        val result = api().deleteAccount()
        store.signOut()
        result
    }

    suspend fun signOut() {
        store.signOut()
    }

    private suspend fun UserStore.syncPreferencesToLocal(user: UserDto) {
        setRegion(user.preferences.region)
        setLanguage(user.preferences.language)
        setStorageLimitGb(user.preferences.storageLimitGb)
        setWifiOnlyDownloads(user.preferences.wifiOnlyDownloads)
        setFavoriteGenres(user.preferences.favoriteGenres)
    }
}
