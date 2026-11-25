package com.universalstreamhub.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.universalstreamhub.app.core.AppGraph
import com.universalstreamhub.app.data.local.HistoryEntity
import com.universalstreamhub.app.data.local.ProgressEntity
import com.universalstreamhub.app.data.local.WatchlistEntity
import com.universalstreamhub.app.data.remote.MediaSummaryDto
import com.universalstreamhub.app.ui.components.EmptyState
import com.universalstreamhub.app.ui.components.HubTopBar
import com.universalstreamhub.app.ui.components.PosterCard
import com.universalstreamhub.app.ui.components.RemoteImage
import com.universalstreamhub.app.ui.theme.HubAccent
import com.universalstreamhub.app.ui.theme.HubMuted
import com.universalstreamhub.app.ui.theme.HubSurface
import com.universalstreamhub.app.ui.theme.HubSurfaceHigh
import com.universalstreamhub.app.ui.vmFactory
import com.universalstreamhub.app.util.formatPlaybackTime

@Composable
fun MyListScreen(
    onOpenDetail: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val viewModel: LibraryViewModel = viewModel(
        factory = vmFactory { LibraryViewModel(AppGraph.container.library, AppGraph.container.auth) },
    )
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val watchlist by viewModel.watchlist.collectAsStateWithLifecycle()
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        HubTopBar()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "My List",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenDownloads) {
                Icon(Icons.Rounded.Download, contentDescription = null, tint = HubAccent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Downloads", color = HubAccent)
            }
        }

        if (!signedIn) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(HubSurface)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Signed out. Your list is saved on this device - sign in to sync across devices.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HubMuted,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenProfile) { Text("Sign in", color = HubAccent) }
            }
        }

        TabRow(
            selectedTabIndex = tab,
            containerColor = HubSurface,
            contentColor = HubAccent,
        ) {
            listOf("Watchlist" to watchlist.size, "Continue" to continueWatching.size, "History" to history.size)
                .forEachIndexed { index, (label, count) ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text("$label ($count)", style = MaterialTheme.typography.labelSmall) },
                    )
                }
        }

        when (tab) {
            0 -> if (watchlist.isEmpty()) {
                EmptyState(
                    title = "Your watchlist is empty",
                    message = "Add titles from any detail page to keep them here. Use Discover or Search to find something.",
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(watchlist, key = { it.id }) { item ->
                        PosterCard(
                            media = item.toSummary(),
                            onClick = { onOpenDetail(item.id) },
                            width = 130,
                        )
                    }
                }
            }

            1 -> if (continueWatching.isEmpty()) {
                EmptyState(
                    title = "Nothing in progress",
                    message = "Titles you start playing appear here with your saved position.",
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(continueWatching, key = { it.id }) { item ->
                        ContinueCard(item = item, onClick = { onOpenDetail(item.id) })
                    }
                }
            }

            else -> if (history.isEmpty()) {
                EmptyState(
                    title = "No viewing history",
                    message = "Everything you open is recorded here for quick access.",
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(history, key = { it.id }) { item ->
                        HistoryCard(item = item, onClick = { onOpenDetail(item.id) })
                    }
                }
            }
        }
    }
}

private fun WatchlistEntity.toSummary() = MediaSummaryDto(
    id = id,
    type = type,
    title = title,
    posterUrl = posterUrl,
    year = year,
    genres = genres.split("\u001F").filter { it.isNotBlank() },
)

@Composable
private fun ContinueCard(item: ProgressEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HubSurface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(
            url = item.posterUrl,
            contentDescription = item.title,
            modifier = Modifier
                .size(width = 76.dp, height = 108.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            val fraction = if (item.durationMs > 0) (item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { fraction },
                color = HubAccent,
                trackColor = HubSurfaceHigh,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${formatPlaybackTime(item.positionMs)} / ${formatPlaybackTime(item.durationMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = HubMuted,
            )
        }
        Spacer(Modifier.width(10.dp))
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = HubAccent),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        ) { Text("Resume", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun HistoryCard(item: HistoryEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(HubSurfaceHigh),
        ) {
            RemoteImage(
                url = item.posterUrl,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(item.title, style = MaterialTheme.typography.bodyMedium)
            Text(item.type.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = HubMuted)
        }
    }
}
