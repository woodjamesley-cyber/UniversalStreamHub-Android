package com.universalstreamhub.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.universalstreamhub.app.AppGraph
import com.universalstreamhub.app.ui.components.ErrorState
import com.universalstreamhub.app.ui.components.HubTopBar
import com.universalstreamhub.app.ui.components.InfoBanner
import com.universalstreamhub.app.ui.components.LoadingState
import com.universalstreamhub.app.ui.components.PosterCard
import com.universalstreamhub.app.ui.theme.HubAccent
import com.universalstreamhub.app.ui.theme.HubBlack
import com.universalstreamhub.app.ui.theme.HubSurface
import com.universalstreamhub.app.ui.vmFactory

@Composable
fun DiscoverScreen(onOpenDetail: (String) -> Unit) {
    val viewModel: DiscoverViewModel = viewModel(factory = vmFactory { DiscoverViewModel(AppGraph.container.catalog) })
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        HubTopBar()

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Chip("Movies", state.type == "movie") { viewModel.selectType("movie") } }
            item { Chip("TV shows", state.type == "tv") { viewModel.selectType("tv") } }
            item { Chip("All genres", state.selectedGenre == null) { viewModel.selectGenre(null) } }
            items(state.genres) { genre ->
                Chip(genre.name, state.selectedGenre == genre.name) { viewModel.selectGenre(genre.name) }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Chip("Any provider", state.selectedProvider == null) { viewModel.selectProvider(null) }
            }
            items(state.providers) { provider ->
                Chip(provider.name, state.selectedProvider.equals(provider.name, true)) {
                    viewModel.selectProvider(provider.name)
                }
            }
        }

        state.notice?.let { InfoBanner(it) }

        when {
            state.loading -> LoadingState("Loading the catalog...")
            state.error != null -> ErrorState(state.error ?: "Could not load the catalog.", onRetry = viewModel::browse)
            state.items.isEmpty() -> LoadingState("No titles matched those filters.")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 118.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.items, key = { it.id }) { media ->
                    PosterCard(media = media, onClick = { onOpenDetail(media.id) }, width = 140)
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = HubAccent,
            selectedLabelColor = HubBlack,
            containerColor = HubSurface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier.padding(end = 2.dp),
    )
}
