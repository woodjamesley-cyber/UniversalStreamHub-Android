package com.universalstreamhub.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.universalstreamhub.app.data.local.HistoryEntity
import com.universalstreamhub.app.data.local.LocalSettings
import com.universalstreamhub.app.data.local.ProgressEntity
import com.universalstreamhub.app.data.local.UserStore
import com.universalstreamhub.app.data.local.WatchlistEntity
import com.universalstreamhub.app.data.remote.GenreDto
import com.universalstreamhub.app.data.remote.HomeFeedDto
import com.universalstreamhub.app.data.remote.MediaSummaryDto
import com.universalstreamhub.app.data.remote.NativeSourceDto
import com.universalstreamhub.app.data.remote.PersonDto
import com.universalstreamhub.app.data.remote.PreferencesDto
import com.universalstreamhub.app.data.remote.ProviderCatalogEntryDto
import com.universalstreamhub.app.data.remote.TitleDetailDto
import com.universalstreamhub.app.data.remote.UserDto
import com.universalstreamhub.app.data.repo.AuthRepository
import com.universalstreamhub.app.data.repo.CatalogRepository
import com.universalstreamhub.app.data.repo.DownloadRepository
import com.universalstreamhub.app.data.repo.LibraryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Error(val message: String) : UiState<Nothing>
    data class Ready<T>(val value: T) : UiState<T>
}

fun <T> UiState<T>.readyValue(): T? = (this as? UiState.Ready<T>)?.value

// ---------------------------------------------------------------------------
// Home
// ---------------------------------------------------------------------------

class HomeViewModel(private val catalog: CatalogRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<HomeFeedDto>>(UiState.Loading)
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            catalog.home()
                .onSuccess { _state.value = UiState.Ready(it) }
                .onFailure { _state.value = UiState.Error(it.message ?: "Could not load the catalog.") }
        }
    }
}

// ---------------------------------------------------------------------------
// Discover
// ---------------------------------------------------------------------------

data class DiscoverState(
    val genres: List<GenreDto> = emptyList(),
    val providers: List<ProviderCatalogEntryDto> = emptyList(),
    val items: List<MediaSummaryDto> = emptyList(),
    val selectedGenre: String? = null,
    val selectedProvider: String? = null,
    val type: String = "movie",
    val loading: Boolean = true,
    val error: String? = null,
    val notice: String? = null,
)

class DiscoverViewModel(private val catalog: CatalogRepository) : ViewModel() {
    private val _state = MutableStateFlow(DiscoverState())
    val state = _state.asStateFlow()

    init {
        loadFilters()
        browse()
    }

    private fun loadFilters() {
        viewModelScope.launch {
            catalog.genres().onSuccess { response -> _state.update { it.copy(genres = response.genres) } }
            catalog.providers().onSuccess { response -> _state.update { it.copy(providers = response.providers) } }
        }
    }

    fun selectGenre(genre: String?) {
        _state.update { it.copy(selectedGenre = genre) }
        browse()
    }

    fun selectProvider(provider: String?) {
        _state.update { it.copy(selectedProvider = provider) }
        browse()
    }

    fun selectType(type: String) {
        _state.update { it.copy(type = type) }
        browse()
    }

    fun browse() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val snapshot = _state.value
            catalog.search(
                query = "",
                type = snapshot.type,
                page = 1,
                year = null,
                genre = snapshot.selectedGenre,
                minRating = null,
                provider = snapshot.selectedProvider,
                language = null,
                sort = "popularity.desc",
            ).onSuccess { response ->
                _state.update {
                    it.copy(loading = false, items = response.items, notice = response.notice, error = null)
                }
            }.onFailure { error ->
                _state.update { it.copy(loading = false, error = error.message ?: "Could not load that collection.") }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------------

data class SearchFilters(
    val type: String = "all",
    val year: Int? = null,
    val genre: String? = null,
    val minRating: Double? = null,
    val provider: String? = null,
    val language: String? = null,
    val sort: String = "popularity.desc",
) {
    val hasFilters: Boolean
        get() = year != null || genre != null || minRating != null || provider != null ||
            language != null || type != "all" || sort != "popularity.desc"
}

data class SearchState(
    val query: String = "",
    val filters: SearchFilters = SearchFilters(),
    val items: List<MediaSummaryDto> = emptyList(),
    val people: List<PersonDto> = emptyList(),
    val recent: List<String> = emptyList(),
    val genres: List<GenreDto> = emptyList(),
    val providers: List<ProviderCatalogEntryDto> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val searched: Boolean = false,
)

class SearchViewModel(
    private val catalog: CatalogRepository,
    private val store: UserStore,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state = _state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            store.settings.collect { settings -> _state.update { it.copy(recent = settings.recentSearches) } }
        }
        viewModelScope.launch {
            catalog.genres().onSuccess { response -> _state.update { it.copy(genres = response.genres) } }
            catalog.providers().onSuccess { response -> _state.update { it.copy(providers = response.providers) } }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        scheduleSearch()
    }

    fun submit() {
        val query = _state.value.query.trim()
        if (query.isNotEmpty()) {
            viewModelScope.launch { store.addRecentSearch(query) }
        }
        runSearch()
    }

    fun applyFilters(filters: SearchFilters) {
        _state.update { it.copy(filters = filters) }
        runSearch()
    }

    fun useRecent(query: String) {
        _state.update { it.copy(query = query) }
        runSearch()
    }

    fun clearRecent() {
        viewModelScope.launch { store.clearRecentSearches() }
    }

    private fun scheduleSearch() {
        job?.cancel()
        job = viewModelScope.launch {
            delay(350)
            runSearchInternal()
        }
    }

    fun runSearch() {
        job?.cancel()
        job = viewModelScope.launch { runSearchInternal() }
    }

    private suspend fun runSearchInternal() {
        val snapshot = _state.value
        val query = snapshot.query.trim()
        if (query.isEmpty() && !snapshot.filters.hasFilters) {
            _state.update { it.copy(items = emptyList(), people = emptyList(), loading = false, error = null, searched = false) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        catalog.search(
            query = query,
            type = snapshot.filters.type,
            page = 1,
            year = snapshot.filters.year,
            genre = snapshot.filters.genre,
            minRating = snapshot.filters.minRating,
            provider = snapshot.filters.provider,
            language = snapshot.filters.language,
            sort = snapshot.filters.sort,
        ).onSuccess { response ->
            _state.update {
                it.copy(
                    loading = false,
                    items = response.items,
                    people = response.people,
                    notice = response.notice,
                    error = null,
                    searched = true,
                )
            }
        }.onFailure { error ->
            _state.update { it.copy(loading = false, error = error.message ?: "Search failed.") }
        }
    }
}

// ---------------------------------------------------------------------------
// Detail
// ---------------------------------------------------------------------------

class DetailViewModel(
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
    private val downloads: DownloadRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<TitleDetailDto>>(UiState.Loading)
    val state = _state.asStateFlow()

    private val _inWatchlist = MutableStateFlow(false)
    val inWatchlist = _inWatchlist.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private var media: MediaSummaryDto? = null
    private var loadedId: String? = null

    fun load(id: String) {
        if (loadedId == id && _state.value is UiState.Ready<*>) return
        loadedId = id
        viewModelScope.launch {
            _state.value = UiState.Loading
            catalog.detail(id).onSuccess { detail ->
                media = detail.media
                _state.value = UiState.Ready(detail)
                resumePosition = library.progressFor(id)?.takeIf { !it.completed }?.positionMs ?: 0L
            }.onFailure { error ->
                _state.value = UiState.Error(error.message ?: "Could not load this title.")
            }
        }
        viewModelScope.launch {
            library.isInWatchlist(id).collect { _inWatchlist.value = it }
        }
    }

    var resumePosition: Long = 0L
        private set

    fun toggleWatchlist() {
        val current = media ?: return
        viewModelScope.launch {
            val added = library.toggleWatchlist(current)
            _message.value = if (added) "${current.title} added to My List" else "${current.title} removed from My List"
        }
    }

    fun download(source: NativeSourceDto) {
        val current = media ?: return
        viewModelScope.launch {
            downloads.enqueue(current, source)
                .onSuccess { _message.value = "Download queued. Track it in My List > Downloads." }
                .onFailure { _message.value = it.message }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}

// ---------------------------------------------------------------------------
// Library / downloads
// ---------------------------------------------------------------------------

class LibraryViewModel(
    private val library: LibraryRepository,
    auth: AuthRepository,
) : ViewModel() {
    val signedIn: StateFlow<Boolean> = auth.signedIn.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val watchlist: StateFlow<List<WatchlistEntity>> =
        library.watchlist.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val continueWatching: StateFlow<List<ProgressEntity>> =
        library.continueWatching.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val history: StateFlow<List<HistoryEntity>> =
        library.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh() {
        viewModelScope.launch { library.refreshFromServer() }
    }
}

data class DownloadsState(
    val entries: List<com.universalstreamhub.app.data.local.DownloadEntity> = emptyList(),
    val usedBytes: Long = 0,
    val quotaBytes: Long = 0,
    val limitGb: Int = 8,
    val wifiOnly: Boolean = true,
    val treeUri: String? = null,
)

class DownloadsViewModel(
    private val downloads: DownloadRepository,
    private val store: UserStore,
) : ViewModel() {
    val state: StateFlow<DownloadsState> = combine(downloads.downloads, store.settings) { entries, settings ->
        DownloadsState(
            entries = entries,
            usedBytes = entries.filter { it.status == "COMPLETE" }.sumOf { it.bytesDownloaded },
            quotaBytes = downloads.quotaBytes(settings.storageLimitGb),
            limitGb = settings.storageLimitGb,
            wifiOnly = settings.wifiOnlyDownloads,
            treeUri = settings.downloadTreeUri,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsState())

    fun delete(id: String) {
        viewModelScope.launch { downloads.deleteDownload(id) }
    }

    fun cancel(id: String) {
        viewModelScope.launch { downloads.cancel(id) }
    }

    fun setLimit(gb: Int) {
        viewModelScope.launch { store.setStorageLimitGb(gb) }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch { store.setWifiOnlyDownloads(enabled) }
    }

    fun setTree(uri: String?) {
        viewModelScope.launch { store.setDownloadTreeUri(uri) }
    }
}

// ---------------------------------------------------------------------------
// Profile
// ---------------------------------------------------------------------------

class ProfileViewModel(
    private val auth: AuthRepository,
    private val catalog: CatalogRepository,
    private val store: UserStore,
) : ViewModel() {
    val user: StateFlow<UserDto?> = auth.user.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val signedIn: StateFlow<Boolean> = auth.signedIn.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val settings: StateFlow<LocalSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalSettings())

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun login(email: String, password: String) = wrap {
        auth.login(email, password).onSuccess {
            _message.value = "Welcome back, ${it.displayName}."
        }.onFailure { _message.value = it.message }
    }

    fun register(email: String, password: String, displayName: String) = wrap {
        auth.register(email, password, displayName).onSuccess {
            _message.value = "Account created. Your library now syncs across devices."
        }.onFailure { _message.value = it.message }
    }

    fun forgotPassword(email: String) = wrap {
        auth.forgotPassword(email).onSuccess { _message.value = it.message }.onFailure { _message.value = it.message }
    }

    fun resetPassword(token: String, password: String) = wrap {
        auth.resetPassword(token, password).onSuccess { _message.value = it.message }.onFailure { _message.value = it.message }
    }

    fun saveProfile(displayName: String, avatarColor: String, preferences: PreferencesDto?) = wrap {
        auth.updateProfile(displayName.ifBlank { null }, avatarColor, preferences)
            .onSuccess { _message.value = "Profile updated." }
            .onFailure { _message.value = it.message }
    }

    fun setDownloadTree(uri: String?) {
        viewModelScope.launch { store.setDownloadTreeUri(uri) }
    }

    fun saveLocal(region: String? = null, language: String? = null, storageLimitGb: Int? = null, wifiOnly: Boolean? = null, favoriteGenres: List<String>? = null) {
        viewModelScope.launch {
            region?.let { store.setRegion(it) }
            language?.let { store.setLanguage(it) }
            storageLimitGb?.let { store.setStorageLimitGb(it) }
            wifiOnly?.let { store.setWifiOnlyDownloads(it) }
            favoriteGenres?.let { store.setFavoriteGenres(it) }
        }
    }

    fun testAndSaveServer(url: String) = wrap {
        catalog.testServer(url)
            .onSuccess {
                store.setApiBaseUrl(url)
                store.setServerReachable(true)
                _message.value = it
            }
            .onFailure {
                store.setServerReachable(false)
                _message.value = it.message
            }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            _message.value = "Signed out. Your local library stays on this device."
        }
    }

    fun deleteAccount() = wrap {
        auth.deleteAccount()
            .onSuccess { _message.value = "Account deleted." }
            .onFailure { _message.value = it.message }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun wrap(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } finally {
                _busy.value = false
            }
        }
    }
}
