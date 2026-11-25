package com.universalstreamhub.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.universalstreamhub.app.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.hubDataStore by preferencesDataStore(name = "hub_preferences")

data class LocalSettings(
    val apiBaseUrl: String = BuildConfig.DEFAULT_API_BASE_URL,
    val region: String = "US",
    val language: String = "en-US",
    val storageLimitGb: Int = 8,
    val wifiOnlyDownloads: Boolean = true,
    val downloadTreeUri: String? = null,
    val favoriteGenres: List<String> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val serverReachable: Boolean? = null,
)

/**
 * App-private preference store. The auth token is kept in DataStore (private
 * app storage) and mirrored in memory so the network interceptor can read it
 * synchronously. Passwords are never stored on the device.
 */
class UserStore(private val context: Context) {

    @Volatile
    private var cachedToken: String? = null

    fun token(): String? = cachedToken

    suspend fun loadToken(): String? {
        val stored = context.hubDataStore.data.first()[KEY_TOKEN]
        cachedToken = stored
        return stored
    }

    val userJson: Flow<String?> = context.hubDataStore.data.map { it[KEY_USER] }

    val settings: Flow<LocalSettings> = context.hubDataStore.data.map { prefs ->
        LocalSettings(
            apiBaseUrl = prefs[KEY_BASE_URL] ?: BuildConfig.DEFAULT_API_BASE_URL,
            region = prefs[KEY_REGION] ?: "US",
            language = prefs[KEY_LANGUAGE] ?: "en-US",
            storageLimitGb = prefs[KEY_STORAGE_LIMIT] ?: 8,
            wifiOnlyDownloads = prefs[KEY_WIFI_ONLY] ?: true,
            downloadTreeUri = prefs[KEY_DOWNLOAD_TREE],
            favoriteGenres = split(prefs[KEY_FAVORITE_GENRES]),
            recentSearches = split(prefs[KEY_RECENT_SEARCHES]),
            serverReachable = prefs[KEY_SERVER_OK]?.let { it == "1" },
        )
    }

    suspend fun saveSession(token: String, userJson: String) {
        cachedToken = token
        context.hubDataStore.edit { prefs ->
            prefs[KEY_TOKEN] = token
            prefs[KEY_USER] = userJson
        }
    }

    suspend fun updateUserJson(userJson: String) {
        context.hubDataStore.edit { it[KEY_USER] = userJson }
    }

    suspend fun signOut() {
        cachedToken = null
        context.hubDataStore.edit { prefs ->
            prefs.remove(KEY_TOKEN)
            prefs.remove(KEY_USER)
        }
    }

    suspend fun setApiBaseUrl(url: String) {
        context.hubDataStore.edit {
            it[KEY_BASE_URL] = url.trim()
            it.remove(KEY_SERVER_OK)
        }
    }

    suspend fun setServerReachable(reachable: Boolean) {
        context.hubDataStore.edit { it[KEY_SERVER_OK] = if (reachable) "1" else "0" }
    }

    suspend fun setRegion(region: String) {
        context.hubDataStore.edit { it[KEY_REGION] = region.uppercase() }
    }

    suspend fun setLanguage(language: String) {
        context.hubDataStore.edit { it[KEY_LANGUAGE] = language }
    }

    suspend fun setStorageLimitGb(gb: Int) {
        context.hubDataStore.edit { it[KEY_STORAGE_LIMIT] = gb }
    }

    suspend fun setWifiOnlyDownloads(wifiOnly: Boolean) {
        context.hubDataStore.edit { it[KEY_WIFI_ONLY] = wifiOnly }
    }

    suspend fun setDownloadTreeUri(uri: String?) {
        context.hubDataStore.edit { prefs ->
            if (uri == null) prefs.remove(KEY_DOWNLOAD_TREE) else prefs[KEY_DOWNLOAD_TREE] = uri
        }
    }

    suspend fun setFavoriteGenres(genres: List<String>) {
        context.hubDataStore.edit { it[KEY_FAVORITE_GENRES] = join(genres) }
    }

    suspend fun addRecentSearch(query: String) {
        if (query.isBlank()) return
        context.hubDataStore.edit { prefs ->
            val existing = split(prefs[KEY_RECENT_SEARCHES]).filterNot { it.equals(query, true) }
            prefs[KEY_RECENT_SEARCHES] = join((listOf(query.trim()) + existing).take(12))
        }
    }

    suspend fun clearRecentSearches() {
        context.hubDataStore.edit { it.remove(KEY_RECENT_SEARCHES) }
    }

    private fun split(value: String?): List<String> =
        value?.split(SEPARATOR)?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    private fun join(values: List<String>): String =
        values.map { it.replace(SEPARATOR, " ") }.filter { it.isNotBlank() }.distinct().joinToString(SEPARATOR)

    private companion object {
        const val SEPARATOR = "\u001F"
        val KEY_TOKEN = stringPreferencesKey("auth_token")
        val KEY_USER = stringPreferencesKey("user_json")
        val KEY_BASE_URL = stringPreferencesKey("api_base_url")
        val KEY_REGION = stringPreferencesKey("region")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_STORAGE_LIMIT = intPreferencesKey("storage_limit_gb")
        val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only_downloads")
        val KEY_DOWNLOAD_TREE = stringPreferencesKey("download_tree_uri")
        val KEY_FAVORITE_GENRES = stringPreferencesKey("favorite_genres")
        val KEY_RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        val KEY_SERVER_OK = stringPreferencesKey("server_ok")
    }
}
