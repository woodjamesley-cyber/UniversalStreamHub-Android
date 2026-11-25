package com.universalstreamhub.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.universalstreamhub.app.data.remote.MediaSummaryDto
import com.universalstreamhub.app.ui.theme.HubAccent
import com.universalstreamhub.app.ui.theme.HubBlack
import com.universalstreamhub.app.ui.theme.HubGold
import com.universalstreamhub.app.ui.theme.HubMuted
import com.universalstreamhub.app.ui.theme.HubSurface
import com.universalstreamhub.app.ui.theme.HubSurfaceHigh
import com.universalstreamhub.app.util.formatRuntime
import com.universalstreamhub.app.util.ratingLabel

@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(modifier = modifier.background(HubSurfaceHigh)) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun HubTopBar(
    subtitle: String? = null,
    onSearch: (() -> Unit)? = null,
    onProfile: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(HubAccent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "UNIVERSAL",
                style = MaterialTheme.typography.labelSmall,
                color = HubMuted,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "STREAM HUB",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
        }
        if (subtitle != null) {
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = HubMuted)
        }
        if (onSearch != null) {
            IconButton(onClick = onSearch) {
                Icon(Icons.Rounded.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (onProfile != null) {
            IconButton(onClick = onProfile) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(HubSurfaceHigh),
                )
            }
        }
    }
}

@Composable
fun PosterCard(
    media: MediaSummaryDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Int = 132,
) {
    Column(
        modifier = modifier
            .width(width.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(14.dp))
                .background(HubSurface),
        ) {
            RemoteImage(
                url = media.posterUrl,
                contentDescription = media.title,
                modifier = Modifier.fillMaxSize(),
            )
            val rating = ratingLabel(media.voteAverage)
            if (rating != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xCC07080C))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Star, contentDescription = null, tint = HubGold, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(rating, style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
            if (media.hasNativePlayback) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xCC41D3A2))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text("FREE", style = MaterialTheme.typography.labelSmall, color = HubBlack, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = media.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = listOfNotNull(media.year?.toString(), formatRuntime(media.runtimeMinutes)).joinToString(" - ")
        if (meta.isNotBlank()) {
            Text(text = meta, style = MaterialTheme.typography.labelSmall, color = HubMuted, maxLines = 1)
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(action, color = HubAccent, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun MediaRow(
    title: String,
    items: List<MediaSummaryDto>,
    onSelect: (MediaSummaryDto) -> Unit,
) {
    if (items.isEmpty()) return
    Column(modifier = Modifier.padding(bottom = 10.dp)) {
        SectionHeader(title = title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.id }) { media ->
                PosterCard(media = media, onClick = { onSelect(media) })
            }
        }
    }
}

@Composable
fun HeroBanner(
    media: MediaSummaryDto,
    onWatch: () -> Unit,
    onDetails: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(430.dp),
    ) {
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
                        colors = listOf(
                            Color(0x2207080C),
                            Color(0x8807080C),
                            HubBlack,
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(
                text = if (media.isOpen) "FREE TO STREAM" else "FEATURED",
                style = MaterialTheme.typography.labelSmall,
                color = HubAccent,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = media.title,
                style = MaterialTheme.typography.displaySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = listOfNotNull(
                    media.year?.toString(),
                    formatRuntime(media.runtimeMinutes),
                    media.genres.take(2).joinToString(" / "),
                ).joinToString("  -  "),
                style = MaterialTheme.typography.bodySmall,
                color = HubMuted,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onWatch,
                    colors = ButtonDefaults.buttonColors(containerColor = HubAccent, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (media.hasNativePlayback) "Watch free" else "Watch", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onDetails,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF), contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Details")
                }
            }
        }
    }
}

@Composable
fun LoadingState(message: String = "Loading the hub...") {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = HubAccent)
            Spacer(Modifier.height(12.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = HubMuted)
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(28.dp),
        ) {
            Text("Something went wrong", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = HubMuted)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = HubAccent),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Try again")
            }
        }
    }
}

@Composable
fun EmptyState(title: String, message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(28.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = HubMuted)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = HubAccent),
                ) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun InfoBanner(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HubSurface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = HubMuted)
    }
}
