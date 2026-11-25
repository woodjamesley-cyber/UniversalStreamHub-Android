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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.universalstreamhub.app.core.AppGraph
import com.universalstreamhub.app.ui.components.InfoBanner
import com.universalstreamhub.app.ui.components.LoadingState
import com.universalstreamhub.app.ui.components.PosterCard
import com.universalstreamhub.app.ui.components.RemoteImage
import com.universalstreamhub.app.ui.theme.HubAccent
import com.universalstreamhub.app.ui.theme.HubBlack
import com.universalstreamhub.app.ui.theme.HubMuted
import com.universalstreamhub.app.ui.theme.HubSurface
import com.universalstreamhub.app.ui.theme.HubSurfaceHigh
import com.universalstreamhub.app.ui.vmFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onOpenDetail: (String) -> Unit) {
    val viewModel: SearchViewModel = viewModel(
        factory = vmFactory { SearchViewModel(AppGraph.container.catalog, AppGraph.container.userStore) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    var showFilters by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Movies, shows, actors, genres") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, keyboardType = KeyboardType.Text),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    viewModel.submit()
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HubAccent,
                    unfocusedBorderColor = HubSurfaceHigh,
                    focusedContainerColor = HubSurface,
                    unfocusedContainerColor = HubSurface,
                ),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { showFilters = true }) {
                Icon(Icons.Rounded.FilterList, contentDescription = "Filters", tint = HubAccent)
            }
        }

        if (state.filters.hasFilters) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (state.filters.type != "all") item { FilterTag(state.filters.type.replaceFirstChar { it.uppercase() }) }
                state.filters.genre?.let { item { FilterTag(it) } }
                state.filters.year?.let { item { FilterTag(it.toString()) } }
                state.filters.minRating?.let { item { FilterTag("${it}+ rating") } }
                state.filters.provider?.let { item { FilterTag(it) } }
                state.filters.language?.let { item { FilterTag(it.uppercase()) } }
                item {
                    TextButton(onClick = { viewModel.applyFilters(SearchFilters()) }) { Text("Clear") }
                }
            }
        }

        state.notice?.let { InfoBanner(it) }

        if (state.recent.isNotEmpty() && !state.searched) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Recent searches", style = MaterialTheme.typography.labelLarge, color = HubMuted, modifier = Modifier.weight(1f))
                TextButton(onClick = viewModel::clearRecent) { Text("Clear", color = HubAccent) }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.recent) { term ->
                    AssistChip(
                        onClick = { viewModel.useRecent(term) },
                        label = { Text(term) },
                        colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.onSurface),
                    )
                }
            }
        }

        when {
            state.loading -> LoadingState("Searching...")
            state.error != null -> LoadingState(state.error ?: "Search failed.")
            !state.searched -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Find something to watch", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Search titles, people and genres across the catalog. Use filters for year, rating, provider and language.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HubMuted,
                    )
                }
            }
            state.items.isEmpty() && state.people.isEmpty() -> LoadingState("No matches. Try a different title or filter.")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 112.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.people.isNotEmpty()) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Column {
                            Text("People", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(state.people) { person ->
                                    Column(
                                        modifier = Modifier
                                            .width(78.dp)
                                            .clickable { viewModel.useRecent(person.name) },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        RemoteImage(
                                            url = person.profileUrl,
                                            contentDescription = person.name,
                                            modifier = Modifier
                                                .size(62.dp)
                                                .clip(CircleShape)
                                                .background(HubSurfaceHigh),
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            person.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 2,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
                items(state.items, key = { it.id }) { media ->
                    PosterCard(media = media, onClick = { onOpenDetail(media.id) }, width = 130)
                }
            }
        }
    }

    if (showFilters) {
        FilterSheet(
            filters = state.filters,
            genres = state.genres.map { it.name },
            providers = state.providers.map { it.name },
            onDismiss = { showFilters = false },
            onApply = {
                viewModel.applyFilters(it)
                showFilters = false
            },
        )
    }
}

@Composable
private fun FilterTag(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(HubSurfaceHigh)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    filters: SearchFilters,
    genres: List<String>,
    providers: List<String>,
    onDismiss: () -> Unit,
    onApply: (SearchFilters) -> Unit,
) {
    var draft by remember { mutableStateOf(filters) }
    var yearText by remember { mutableStateOf(filters.year?.toString() ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HubSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("Filters", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))

            Text("Type", style = MaterialTheme.typography.labelLarge, color = HubMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "All", "movie" to "Movies", "tv" to "TV").forEach { (value, label) ->
                    FilterChip(
                        selected = draft.type == value,
                        onClick = { draft = draft.copy(type = value) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HubAccent, selectedLabelColor = HubBlack),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = yearText,
                onValueChange = { value ->
                    yearText = value.filter { it.isDigit() }.take(4)
                    draft = draft.copy(year = yearText.toIntOrNull())
                },
                label = { Text("Release year") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))
            Text("Minimum rating", style = MaterialTheme.typography.labelLarge, color = HubMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf<Double?>(null, 6.0, 7.0, 8.0, 9.0).forEach { value ->
                    FilterChip(
                        selected = draft.minRating == value,
                        onClick = { draft = draft.copy(minRating = value) },
                        label = { Text(value?.let { "$it+" } ?: "Any") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HubAccent, selectedLabelColor = HubBlack),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("Language", style = MaterialTheme.typography.labelLarge, color = HubMuted)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = draft.language == null,
                        onClick = { draft = draft.copy(language = null) },
                        label = { Text("Any") },
                    )
                }
                items(listOf("en", "es", "fr", "de", "hi", "ja", "ko")) { code ->
                    FilterChip(
                        selected = draft.language == code,
                        onClick = { draft = draft.copy(language = code) },
                        label = { Text(code.uppercase()) },
                    )
                }
            }

            if (genres.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Genre", style = MaterialTheme.typography.labelLarge, color = HubMuted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = draft.genre == null,
                            onClick = { draft = draft.copy(genre = null) },
                            label = { Text("Any") },
                        )
                    }
                    items(genres) { genre ->
                        FilterChip(
                            selected = draft.genre == genre,
                            onClick = { draft = draft.copy(genre = if (draft.genre == genre) null else genre) },
                            label = { Text(genre) },
                        )
                    }
                }
            }

            if (providers.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Streaming provider", style = MaterialTheme.typography.labelLarge, color = HubMuted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = draft.provider == null,
                            onClick = { draft = draft.copy(provider = null) },
                            label = { Text("Any") },
                        )
                    }
                    items(providers) { provider ->
                        FilterChip(
                            selected = draft.provider == provider,
                            onClick = { draft = draft.copy(provider = if (draft.provider == provider) null else provider) },
                            label = { Text(provider) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("Sort by", style = MaterialTheme.typography.labelLarge, color = HubMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("popularity.desc" to "Popular", "vote_average.desc" to "Rating", "primary_release_date.desc" to "Newest").forEach { (value, label) ->
                    FilterChip(
                        selected = draft.sort == value,
                        onClick = { draft = draft.copy(sort = value) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HubAccent, selectedLabelColor = HubBlack),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onApply(SearchFilters()) },
                    colors = ButtonDefaults.buttonColors(containerColor = HubSurfaceHigh),
                    modifier = Modifier.weight(1f),
                ) { Text("Reset") }
                Button(
                    onClick = { onApply(draft) },
                    colors = ButtonDefaults.buttonColors(containerColor = HubAccent),
                    modifier = Modifier.weight(2f),
                ) { Text("Apply filters") }
            }
        }
    }
}
