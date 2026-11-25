package com.universalstreamhub.app.ui.navigation

object Routes {
    const val HOME = "home"
    const val DISCOVER = "discover"
    const val SEARCH = "search"
    const val MY_LIST = "my_list"
    const val PROFILE = "profile"
    const val DOWNLOADS = "downloads"
    const val DETAIL = "detail/{mediaId}"

    val mainRoutes = setOf(HOME, DISCOVER, SEARCH, MY_LIST, PROFILE)

    fun detail(mediaId: String): String = "detail/${android.net.Uri.encode(mediaId)}"
}
