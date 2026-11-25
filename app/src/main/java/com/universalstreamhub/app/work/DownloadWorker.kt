package com.universalstreamhub.app.work

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.universalstreamhub.app.AppGraph
import com.universalstreamhub.app.data.local.DownloadEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * Streams an authorized (public-domain / CC) video to local storage, either in
 * the app's media directory or an SD card / USB folder chosen with the system
 * folder picker. Progress is persisted so the UI can show it.
 */
class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val container = AppGraph.container
        val dao = container.db.downloadDao()

        val id = inputData.getString(KEY_ID) ?: return@withContext Result.failure()
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return@withContext Result.failure()
        val treeUri = inputData.getString(KEY_TREE)
        val wifiOnly = inputData.getBoolean(KEY_WIFI_ONLY, true)

        if (wifiOnly && !container.downloads.isOnWifi()) {
            dao.save(pending(id, "WAITING_FOR_WIFI", "Waiting for Wi-Fi (off in Profile > Offline storage)."))
            return@withContext Result.retry()
        }

        val entry = dao.find(id) ?: return@withContext Result.failure()
        dao.save(entry.copy(status = "RUNNING", errorMessage = null, updatedAt = System.currentTimeMillis()))

        var output: OutputStream? = null
        val targetUri: String
        try {
            if (treeUri != null) {
                val tree = DocumentFile.fromTreeUri(applicationContext, Uri.parse(treeUri))
                    ?: return@withContext fail(dao, entry, "The chosen download folder is no longer available.")
                val existing = tree.findFile(fileName)
                val document = existing ?: tree.createFile("video/mp4", fileName)
                    ?: return@withContext fail(dao, entry, "Could not create the file in that folder.")
                targetUri = document.uri.toString()
                output = applicationContext.contentResolver.openOutputStream(document.uri)
                    ?: return@withContext fail(dao, entry, "Could not open that folder for writing.")
            } else {
                val directory = File(container.downloads.downloadDirectory().absolutePath)
                if (!directory.exists()) directory.mkdirs()
                val file = File(directory, fileName)
                targetUri = file.absolutePath
                output = file.outputStream()
            }
        } catch (error: Exception) {
            return@withContext fail(dao, entry, error.message ?: "Could not open the download destination.")
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "UniversalStreamHub-Android/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext fail(dao, entry, "The source returned HTTP ${response.code}.")
                }
                val body = response.body ?: return@withContext fail(dao, entry, "The source returned no data.")
                val total = body.contentLength().takeIf { it > 0 } ?: entry.bytesTotal
                val buffer = ByteArray(64 * 1024)
                var written = 0L
                var lastPersisted = 0L

                body.byteStream().use { input ->
                    while (!isStopped) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output?.write(buffer, 0, read)
                        written += read
                        if (written - lastPersisted > 2L * 1024L * 1024L) {
                            lastPersisted = written
                            setProgress(progressData(written, total))
                            dao.save(
                                entry.copy(
                                    status = "RUNNING",
                                    bytesDownloaded = written,
                                    bytesTotal = total,
                                    fileUri = targetUri,
                                    updatedAt = System.currentTimeMillis(),
                                ),
                            )
                        }
                    }
                }

                output?.flush()
                if (written <= 0) return@withContext fail(dao, entry, "The download produced an empty file.")
                dao.save(
                    entry.copy(
                        status = "COMPLETE",
                        bytesDownloaded = written,
                        bytesTotal = if (total > 0) total else written,
                        fileUri = targetUri,
                        errorMessage = null,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                setProgress(progressData(written, total))
                Result.success()
            }
        } catch (error: Exception) {
            if (isStopped) {
                dao.save(entry.copy(status = "FAILED", errorMessage = "Cancelled", updatedAt = System.currentTimeMillis()))
                Result.failure()
            } else {
                fail(dao, entry, error.message ?: "The download failed.")
            }
        } finally {
            runCatching { output?.close() }
        }
    }

    private suspend fun fail(dao: com.universalstreamhub.app.data.local.DownloadDao, entry: DownloadEntity, message: String): Result {
        dao.save(entry.copy(status = "FAILED", errorMessage = message, updatedAt = System.currentTimeMillis()))
        return Result.failure()
    }

    private fun pending(id: String, status: String, message: String): DownloadEntity = DownloadEntity(
        id = id,
        title = inputData.getString(KEY_TITLE) ?: "",
        posterUrl = inputData.getString(KEY_POSTER),
        sourceUrl = inputData.getString(KEY_URL) ?: "",
        fileUri = "",
        bytesTotal = inputData.getLong(KEY_TOTAL, 0),
        bytesDownloaded = 0,
        status = status,
        quality = inputData.getString(KEY_QUALITY),
        license = inputData.getString(KEY_LICENSE),
        errorMessage = message,
        updatedAt = System.currentTimeMillis(),
    )

    private fun progressData(written: Long, total: Long) = androidx.work.workDataOf(
        "bytesDownloaded" to written,
        "bytesTotal" to total,
    )

    companion object {
        const val KEY_ID = "id"
        const val KEY_URL = "url"
        const val KEY_TITLE = "title"
        const val KEY_POSTER = "poster"
        const val KEY_QUALITY = "quality"
        const val KEY_LICENSE = "license"
        const val KEY_TOTAL = "total"
        const val KEY_TREE = "tree"
        const val KEY_FILE_NAME = "fileName"
        const val KEY_WIFI_ONLY = "wifiOnly"
    }
}
