package com.universalstreamhub.app.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.universalstreamhub.app.core.AppGraph
import com.universalstreamhub.app.data.local.DownloadEntity
import com.universalstreamhub.app.data.remote.MediaSummaryDto
import com.universalstreamhub.app.player.PlayerActivity
import com.universalstreamhub.app.ui.components.HubTopBar
import com.universalstreamhub.app.ui.components.RemoteImage
import com.universalstreamhub.app.ui.theme.HubAccent
import com.universalstreamhub.app.ui.theme.HubBlack
import com.universalstreamhub.app.ui.theme.HubMuted
import com.universalstreamhub.app.ui.theme.HubSurface
import com.universalstreamhub.app.ui.theme.HubSurfaceHigh
import com.universalstreamhub.app.ui.vmFactory
import com.universalstreamhub.app.util.formatBytes
import com.universalstreamhub.app.util.formatStorageLimit

@Composable
fun DownloadsScreen(onBack: () -> Unit, onManageStorage: () -> Unit) {
    val viewModel: DownloadsViewModel = viewModel(
        factory = vmFactory { DownloadsViewModel(AppGraph.container.downloads, AppGraph.container.userStore) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.setTree(uri.toString())
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
            }
            HubTopBar()
        }

        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(HubSurface)
                        .padding(16.dp),
                ) {
                    Text("Offline storage", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${formatBytes(state.usedBytes)} used of ${formatStorageLimit(state.limitGb)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = HubMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    val fraction = if (state.quotaBytes > 0 && state.quotaBytes != Long.MAX_VALUE) {
                        (state.usedBytes.toFloat() / state.quotaBytes).coerceIn(0f, 1f)
                    } else 0f
                    LinearProgressIndicator(
                        progress = { fraction },
                        color = HubAccent,
                        trackColor = HubSurfaceHigh,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Storage allowance", style = MaterialTheme.typography.labelLarge, color = HubMuted)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(4, 8, 16, 32, 64, -1)) { gb ->
                            FilterChip(
                                selected = state.limitGb == gb,
                                onClick = { viewModel.setLimit(gb) },
                                label = { Text(formatStorageLimit(gb)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = HubAccent,
                                    selectedLabelColor = HubBlack,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Download over Wi-Fi only", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Downloads wait for Wi-Fi when enabled.",
                                style = MaterialTheme.typography.labelSmall,
                                color = HubMuted,
                            )
                        }
                        Switch(checked = state.wifiOnly, onCheckedChange = viewModel::setWifiOnly)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { folderPicker.launch(null) },
                            colors = ButtonDefaults.buttonColors(containerColor = HubSurfaceHigh, contentColor = MaterialTheme.colorScheme.onSurface),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.treeUri == null) "Choose folder (SD card / USB)" else "Change folder")
                        }
                    }
                    if (state.treeUri != null) {
                        TextButton(onClick = { viewModel.setTree(null) }) {
                            Text("Use app storage instead", color = HubAccent)
                        }
                    }
                    Text(
                        "Only titles we are licensed to store can be downloaded: the free public-domain catalog. " +
                            "Netflix, Disney+ and other subscription titles can only be downloaded inside their own apps.",
                        style = MaterialTheme.typography.labelSmall,
                        color = HubMuted,
                    )
                    TextButton(onClick = onManageStorage) { Text("Manage profile", color = HubAccent) }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                Text("Downloads", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
            }

            if (state.entries.isEmpty()) {
                item {
                    Text(
                        "Nothing downloaded yet. Open a free title and tap Download for offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HubMuted,
                    )
                }
            }

            items(state.entries, key = { it.id }) { entry ->
                DownloadRow(
                    entry = entry,
                    onPlay = {
                        val media = MediaSummaryDto(
                            id = entry.id,
                            type = "movie",
                            title = entry.title,
                            posterUrl = entry.posterUrl,
                            playback = "NATIVE",
                        )
                        PlayerActivity.start(context, entry.fileUri, media)
                    },
                    onDelete = { viewModel.delete(entry.id) },
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(entry: DownloadEntity, onPlay: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HubSurface)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(
            url = entry.posterUrl,
            contentDescription = entry.title,
            modifier = Modifier
                .size(width = 60.dp, height = 86.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Text(
                buildString {
                    append(entry.status.replaceFirstChar { it.uppercase() })
                    entry.quality?.let { append(" - $it") }
                    if (entry.bytesTotal > 0) append(" - ${formatBytes(entry.bytesTotal)}")
                },
                style = MaterialTheme.typography.labelSmall,
                color = HubMuted,
            )
            if (entry.status == "RUNNING" || entry.status == "QUEUED" || entry.status == "WAITING_FOR_WIFI") {
                Spacer(Modifier.height(6.dp))
                val fraction = if (entry.bytesTotal > 0) {
                    (entry.bytesDownloaded.toFloat() / entry.bytesTotal).coerceIn(0f, 1f)
                } else 0f
                LinearProgressIndicator(
                    progress = { fraction },
                    color = HubAccent,
                    trackColor = HubSurfaceHigh,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    formatBytes(entry.bytesDownloaded),
                    style = MaterialTheme.typography.labelSmall,
                    color = HubMuted,
                )
            }
            entry.errorMessage?.let {
                if (entry.status == "FAILED") {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (entry.status == "COMPLETE") {
            IconButton(onClick = onPlay) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play download", tint = HubAccent)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, contentDescription = "Delete download", tint = HubMuted)
        }
    }
}
