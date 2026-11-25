package com.universalstreamhub.app.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

class HubException(message: String, val code: String = "error", cause: Throwable? = null) : Exception(message, cause)

/**
 * Owns the OkHttp/Retrofit stack. The base URL can change at runtime (Profile >
 * Hub server), so clients are cached per normalised URL.
 */
class ApiClient(private val tokenProvider: () -> String?) {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(35, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val builder = chain.request().newBuilder()
                .header("Accept", "application/json")
                .header("User-Agent", "UniversalStreamHub-Android/1.0")
            tokenProvider()?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
            chain.proceed(builder.build())
        }
        .build()

    private val clients = mutableMapOf<String, HubApi>()

    fun api(baseUrl: String): HubApi {
        val normalised = normaliseBaseUrl(baseUrl)
        synchronized(clients) {
            clients[normalised]?.let { return it }
            val api = Retrofit.Builder()
                .baseUrl(normalised)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(HubApi::class.java)
            clients[normalised] = api
            return api
        }
    }

    companion object {
        fun normaliseBaseUrl(raw: String): String {
            val trimmed = raw.trim()
            val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "https://$trimmed"
            }
            return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
        }
    }
}

/** Maps transport and API errors to a message that can be shown in the UI. */
suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (error: HttpException) {
    val message = parseApiError(error)
    Result.failure(HubException(message, "http_${error.code()}", error))
} catch (error: IOException) {
    Result.failure(
        HubException(
            "Can't reach the hub server. Check your connection or the server address in Profile > Hub server.",
            "network",
            error,
        ),
    )
} catch (error: Exception) {
    Result.failure(HubException(error.message ?: "Something went wrong.", "unexpected", error))
}

private fun parseApiError(error: HttpException): String {
    val body = try {
        error.response()?.errorBody()?.string()
    } catch (_: Exception) {
        null
    }
    if (!body.isNullOrBlank()) {
        try {
            val parsed = Json { ignoreUnknownKeys = true }.decodeFromString(ApiErrorEnvelopeDto.serializer(), body)
            parsed.error?.message?.takeIf { it.isNotBlank() }?.let { return it }
        } catch (_: Exception) {
            // fall through
        }
    }
    return when (error.code()) {
        401 -> "Your session has expired. Please sign in again."
        403 -> "That action is not allowed."
        404 -> "Not found."
        429 -> "Too many attempts. Please wait and try again."
        in 500..599 -> "The hub server had a problem. Please try again."
        else -> "Request failed (HTTP ${error.code()})."
    }
}
