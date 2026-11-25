package com.universalstreamhub.app.data.repo

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.universalstreamhub.app.data.local.AppDatabase
import com.universalstreamhub.app.data.local.DownloadEntity
import com.universalstreamhub.app.data.local.LocalSettings
import com.universalstreamhub.app.data.local.UserStore
import com.universalstreamhub.app.data.remote.HubException
import com.universalstreamhub.app.data.remote.MediaSummaryDto
import com.universalstreamhub.app.data.remote.NativeSourceDto
import com.universalstreamhub.app.util.formatBytes
import com.universalstreamhub.app.work.DownloadWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Offline downloads for titles the project is licensed to store: the free
 * public-domain / Creative Commons catalog. Licensed streaming titles are
 * DRM-protected and can only be downloaded inside the provider's own app, so
 * they are rejected here rather than faked.
 */
class DownloadRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val store: UserStore,
) {
    val downloads: Flow<List<DownloadEntity>> = db.downloadDao().downloads()

    suspend fun settings(): LocalSettings = store.settings.first()

    suspend fun usedBytes(): Long = db.downloadDao().usedBytes()

    fun quotaBytes(limitGb: Int): Long =
        if (limitGb <= 0) Long.MAX_VALUE else limitGb.toLong() * 1024L * 1024L * 1024L

    suspend fun freeDiskBytes(): Long {
        val target = context.getExternalFilesDir(null) ?: context.filesDir
        return target.usableSpace
    }

    suspend fun storageLimitGb(): Int = store.settings.first().storageLimitGb

    /**
     * Queues a download after checking the user's storage allowance and the
     * device's free space. Returns the download id.
     */
    suspend fun enqueue(media: MediaSummaryDto, source: NativeSourceDto): Result<String> {
        if (!source.downloadAllowed) {
            return Result.failure(HubException("This title is not licensed for offline download."))
        }
        val existing = db.downloadDao().find(media.id)
        if (existing?.status == "COMPLETE") {
            return Result.failure(HubException("${media.title} is already downloaded."))
        }

        val settings = store.settings.first()
        val expected = source.fileSizeBytes ?: 0L
        val used = db.downloadDao().usedBytes()
        val quota = quotaBytes(settings.storageLimitGb)
        if (used + expected > quota) {
            val remaining = (quota - used).coerceAtLeast(0)
            return Result.failure(
                HubException(
                    "Your offline storage allowance is ${formatGb(settings.storageLimitGb)} with " +
                        "${formatBytes(remaining)} free. This download needs " +
                        "${formatBytes(expected)}. Raise the limit in " +
                        "Profile > Offline storage or remove a download.",
                ),
            )
        }
        if (freeDiskBytes() < expected + 64L * 1024L * 1024L) {
            return Result.failure(HubException("This device does not have enough free space for that download."))
        }

        val fileName = fileNameFor(media)
        val fileUri = if (settings.downloadTreeUri != null) {
            "pending"
        } else {
            File(downloadDirectory(), fileName).absolutePath
        }

        db.downloadDao().save(
            DownloadEntity(
                id = media.id,
                title = media.title,
                posterUrl = media.posterUrl,
                sourceUrl = source.url,
                fileUri = fileUri,
                bytesTotal = expected,
                bytesDownloaded = 0,
                status = "QUEUED",
                quality = source.quality,
                license = source.license,
                errorMessage = null,
                updatedAt = System.currentTimeMillis(),
            ),
        )

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_ID to media.id,
                    DownloadWorker.KEY_URL to source.url,
                    DownloadWorker.KEY_TITLE to media.title,
                    DownloadWorker.KEY_POSTER to media.posterUrl,
                    DownloadWorker.KEY_QUALITY to source.quality,
                    DownloadWorker.KEY_LICENSE to source.license,
                    DownloadWorker.KEY_TOTAL to expected,
                    DownloadWorker.KEY_TREE to settings.downloadTreeUri,
                    DownloadWorker.KEY_FILE_NAME to fileName,
                    DownloadWorker.KEY_WIFI_ONLY to settings.wifiOnlyDownloads,
                ),
            )
            .addTag("hub-download")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "download:${media.id}",
            ExistingWorkPolicy.KEEP,
            request,
        )
        return Result.success(media.id)
    }

    suspend fun cancel(id: String) {
        WorkManager.getInstance(context).cancelUniqueWork("download:$id")
        removeFile(id)
        db.downloadDao().delete(id)
    }

    suspend fun deleteDownload(id: String) {
        WorkManager.getInstance(context).cancelUniqueWork("download:$id")
        removeFile(id)
        db.downloadDao().delete(id)
    }

    private suspend fun removeFile(id: String) {
        val entry = db.downloadDao().find(id) ?: return
        runCatching {
            when {
                entry.fileUri.startsWith("content://") ->
                    DocumentFile.fromSingleUri(context, Uri.parse(entry.fileUri))?.delete()
                entry.fileUri.startsWith("/") -> File(entry.fileUri).delete()
                else -> Unit
            }
        }
    }

    fun downloadDirectory(): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, "downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun fileNameFor(media: MediaSummaryDto): String {
        val safe = media.title.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(" ", "_").take(60)
        return "${safe.ifBlank { "title" }}_${media.id.hashCode().toUInt().toString(16)}.mp4"
    }

    fun isOnWifi(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun formatGb(value: Int): String = when {
        value <= 0 -> "Unlimited"
        value >= 1024 -> "${value / 1024} TB"
        else -> "$value GB"
    }
}
