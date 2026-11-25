package com.universalstreamhub.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.universalstreamhub.app.core.AppGraph
import com.universalstreamhub.app.data.remote.MediaSummaryDto
import com.universalstreamhub.app.data.remote.NativeSourceDto
import com.universalstreamhub.app.data.remote.ProviderLinkDto
import com.universalstreamhub.app.data.remote.TitleDetailDto
import com.universalstreamhub.app.player.PlayerActivity
import com.universalstreamhub.app.ui.components.ErrorState
import com.universalstreamhub.app.ui.components.LoadingState
import com.universalstreamhub.app.ui.components.MediaRow
import com.universalstreamhub.app.ui.components.RemoteImage
import com.universalstreamhub.app.ui.components.SectionHeader
import com.universalstreamhub.app.ui.theme.HubAccent
import com.universalstreamhub.app.ui.theme.HubBlack
import com.universalstreamhub.app.ui.theme.HubGold
import com.universalstreamhub.app.ui.theme.HubMuted
import com.universalstreamhub.app.ui.theme.HubSurface
import com.universalstreamhub.app.ui.theme.HubSurfaceHigh
import com.universalstreamhub.app.ui.vmFactory
import com.universalstreamhub.app.util.formatBytes
import com.universalstreamhub.app.util.formatRuntime
import com.universalstreamhub.app.util.ratingLabel

@Composable
fun DetailScreen(
    mediaId: String,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val viewModel: DetailViewModel = viewModel(
        key = "detail-$mediaId",
        factory = vmFactory {
            DetailViewModel(
                catalog = AppGraph.container.catalog,
                library = AppGraph.container.library,
                downloads = AppGraph.container.downloads,
            )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val inWatchlist by viewModel.inWatchlist.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(mediaId) { viewModel.load(mediaId) }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val detail = state.readyValue()

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            detail != null -> DetailContent(
                detail = detail,
                inWatchlist = inWatchlist,
                onBack = onBack,
                onToggleWatchlist = viewModel::toggleWatchlist,
                onPlay = { source ->
                    PlayerActivity.start(
                        context = context,
                        url = source.url,
                        media = detail.media,
                        startPositionMs = viewModel.resumePosition,
                    )
                },
                onProvider = { link -> openProvider(context, link) {} },
                onDownload = viewModel::download,
                onOpenDetail = onOpenDetail,
            )
            state is UiState.Error -> ErrorState((state as UiState.Error).message, onRetry = { viewModel.load(mediaId) })
            else -> LoadingState("Loading title...")
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun DetailContent(
    detail: TitleDetailDto,
    inWatchlist: Boolean,
    onBack: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onPlay: (NativeSourceDto) -> Unit,
    onProvider: (ProviderLinkDto) -> Unit,
    onDownload: (NativeSourceDto) -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val media = detail.media
    val native = detail.playback.native
    val providerApps = detail.playback.providerApps.ifEmpty { media.providers }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                RemoteImage(
                    url = media.backdropUrl ?: media.posterUrl,
                    contentDescription = media.title,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x5507080C), Color(0xAA07080C), HubBlack),
                            ),
                        ),
                )
                IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 0.dp),
            ) {
                RemoteImage(
                    url = media.posterUrl,
                    contentDescription = media.title,
                    modifier = Modifier
                        .width(110.dp)
                        .height(160.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        media.title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        listOfNotNull(
                            media.year?.toString(),
                            formatRuntime(media.runtimeMinutes),
                            media.type.replaceFirstChar { it.uppercase() },
                        ).joinToString("  -  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = HubMuted,
                    )
                    ratingLabel(media.voteAverage)?.let { rating ->
                        Spacer(Modifier.height(4.dp))
                        Text("$rating / 10", style = MaterialTheme.typography.bodySmall, color = HubGold)
                    }
                    media.genres.take(3).forEach { genre ->
                        Text(genre, style = MaterialTheme.typography.labelSmall, color = HubMuted)
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                if (native.isNotEmpty()) {
                    Button(
                        onClick = { onPlay(native.first()) },
                        colors = ButtonDefaults.buttonColors(containerColor = HubAccent),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (detail.playback.native.size > 1) "Watch now - ${native.size} sources" else "Watch now",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else if (providerApps.isNotEmpty()) {
                    Button(
                        onClick = { onProvider(providerApps.first()) },
                        colors = ButtonDefaults.buttonColors(containerColor = HubAccent),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open in ${providerApps.first().name}", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onToggleWatchlist,
                        colors = ButtonDefaults.buttonColors(containerColor = HubSurfaceHigh),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            if (inWatchlist) Icons.Rounded.Check else Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (inWatchlist) "In My List" else "Add to My List")
                    }
                    detail.trailer?.let { trailer ->
                        val context = LocalContext.current
                        Button(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(trailer.webUrl.ifBlank { "https://www.youtube.com/watch?v=${trailer.youtubeKey}" })),
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = HubSurfaceHigh),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("Trailer") }
                    }
                }
            }
        }

        if (detail.playback.notes.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(HubSurface)
                        .padding(14.dp),
                ) {
                    detail.playback.notes.forEach { note ->
                        Text("- $note", style = MaterialTheme.typography.labelSmall, color = HubMuted)
                    }
                    if (media.license != null) {
                        Spacer(Modifier.height(6.dp))
                        Text("Licence: ${media.license}", style = MaterialTheme.typography.labelSmall, color = HubMuted)
                    }
                }
            }
        }

        if (media.overview.isNotBlank()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text("Overview", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(media.overview, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (native.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text("Sources & downloads", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    native.forEach { source ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(HubSurface)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(source.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    listOfNotNull(
                                        source.quality,
                                        source.fileSizeBytes?.takeIf { it > 0 }?.let { formatBytes(it) },
                                        source.license.takeIf { it.isNotBlank() },
                                    ).joinToString(" - "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HubMuted,
                                )
                            }
                            if (source.downloadAllowed) {
                                IconButton(onClick = { onDownload(source) }) {
                                    Icon(Icons.Rounded.Download, contentDescription = "Download", tint = HubAccent)
                                }
                            }
                            IconButton(onClick = { onPlay(source) }) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = HubAccent)
                            }
                        }
                    }
                }
            }
        }

        if (providerApps.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Text("Available on", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    providerApps.forEach { link ->
                        ProviderRow(link = link, onClick = { onProvider(link) })
                    }
                }
            }
        }

        if (detail.cast.isNotEmpty()) {
            item {
                SectionHeader(title = "Cast")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(detail.cast.take(16)) { person ->
                        Column(
                            modifier = Modifier.width(78.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            RemoteImage(
                                url = person.profileUrl,
                                contentDescription = person.name,
                                modifier = Modifier
                                    .size(66.dp)
                                    .clip(CircleShape)
                                    .background(HubSurfaceHigh),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(person.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            person.character?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = HubMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        val directors = detail.crew.filter { it.job.equals("Director", true) }
        if (directors.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("Directed by", style = MaterialTheme.typography.labelLarge, color = HubMuted)
                    Text(directors.joinToString(", ") { it.name }, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        detail.similar.takeIf { it.isNotEmpty() }?.let { similar ->
            item {
                MediaRow(title = "More like this", items = similar, onSelect = { onOpenDetail(it.id) })
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ProviderRow(link: ProviderLinkDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(HubSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(
            url = link.logoUrl,
            contentDescription = link.name,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(link.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                when {
                    link.offlineInOwnApp -> "Watch in the ${link.name} app (offline downloads available there)"
                    else -> "Opens the official ${link.name} app or site"
                },
                style = MaterialTheme.typography.labelSmall,
                color = HubMuted,
            )
        }
        Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = HubAccent, modifier = Modifier.size(16.dp))
    }
}

/** Opens the provider's own Android app via its package, falling back to the official site. */
fun openProvider(context: Context, link: ProviderLinkDto, onFailure: () -> Unit) {
    val packageIntent = link.packageName.takeIf { it.isNotBlank() }?.let { pkg ->
        context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (link.webUrl.isNotBlank()) {
                data = Uri.parse(link.webUrl)
            }
        }
    }
    if (packageIntent != null) {
        try {
            context.startActivity(packageIntent)
            return
        } catch (_: ActivityNotFoundException) {
            // fall through to the web URL
        }
    }
    if (link.webUrl.isNotBlank()) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(link.webUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        } catch (_: ActivityNotFoundException) {
            // no browser available
        }
    }
    onFailure()
}
