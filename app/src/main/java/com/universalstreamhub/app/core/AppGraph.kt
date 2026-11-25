package com.universalstreamhub.app.core

import android.content.Context
import com.universalstreamhub.app.data.local.AppDatabase
import com.universalstreamhub.app.data.local.UserStore
import com.universalstreamhub.app.data.remote.ApiClient
import com.universalstreamhub.app.data.repo.AuthRepository
import com.universalstreamhub.app.data.repo.CatalogRepository
import com.universalstreamhub.app.data.repo.DownloadRepository
import com.universalstreamhub.app.data.repo.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase = AppDatabase.build(appContext)
    val userStore = UserStore(appContext)
    val apiClient = ApiClient { userStore.token() }

    val auth = AuthRepository(userStore, apiClient)
    val catalog = CatalogRepository(userStore, apiClient)
    val library = LibraryRepository(database, userStore, apiClient)
    val downloads = DownloadRepository(appContext, database, userStore)

    val scope: CoroutineScope get() = applicationScope

    init {
        applicationScope.launch {
            runCatching {
                val token = userStore.loadToken()
                if (!token.isNullOrBlank()) {
                    library.syncWithServer()
                }
                runCatching { catalog.health() }
            }
        }
    }
}

object AppGraph {
    @Volatile
    private var containerRef: AppContainer? = null

    val container: AppContainer
        get() = containerRef ?: error("AppGraph has not been initialised")

    fun initialise(context: Context) {
        if (containerRef == null) {
            synchronized(this) {
                if (containerRef == null) containerRef = AppContainer(context)
            }
        }
    }
}
