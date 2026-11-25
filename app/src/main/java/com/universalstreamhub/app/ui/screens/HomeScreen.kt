package com.universalstreamhub.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.universalstreamhub.app.AppGraph
import com.universalstreamhub.app.data.remote.HomeFeedDto
import com.universalstreamhub.app.ui.components.ErrorState
import com.universalstreamhub.app.ui.components.HeroBanner
import com.universalstreamhub.app.ui.components.HubTopBar
import com.universalstreamhub.app.ui.components.InfoBanner
import com.universalstreamhub.app.ui.components.LoadingState
import com.universalstreamhub.app.ui.components.MediaRow
import com.universalstreamhub.app.ui.theme.HubMuted
import com.universalstreamhub.app.ui.vmFactory

@Composable
fun HomeScreen(
    onOpenDetail: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(factory = vmFactory { HomeViewModel(AppGraph.container.catalog) })
    val state by viewModel.state.collectAsStateWithLifecycle()

    val feed = state.readyValue()
    when {
        feed != null -> HomeContent(
            feed = feed,
            onOpenDetail = onOpenDetail,
            onOpenSearch = onOpenSearch,
            onOpenProfile = onOpenProfile,
        )
        state is UiState.Error -> ErrorState(message = state.message, onRetry = viewModel::load)
        else -> LoadingState()
    }
}

@Composable
private fun HomeContent(
    feed: HomeFeedDto,
    onOpenDetail: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            HubTopBar(
                subtitle = if (feed.tmdbConfigured) "LIVE CATALOG" else "FREE CATALOG",
                onSearch = onOpenSearch,
                onProfile = onOpenProfile,
            )
        }
        feed.hero?.let { hero ->
            item {
                HeroBanner(
                    media = hero,
                    onWatch = { onOpenDetail(hero.id) },
                    onDetails = { onOpenDetail(hero.id) },
                )
            }
        }
        feed.notice?.let { notice ->
            item { InfoBanner(text = notice) }
        }
        items(feed.rows) { row ->
            MediaRow(title = row.title, items = row.items, onSelect = { onOpenDetail(it.id) })
        }
        item {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Universal Stream Hub aggregates catalogs from licensed providers. " +
                        "Free public-domain titles play in the native player; licensed titles open in the provider's official app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HubMuted,
                )
            }
        }
    }
}
