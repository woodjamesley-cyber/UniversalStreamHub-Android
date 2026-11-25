package com.universalstreamhub.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.universalstreamhub.app.ui.navigation.Routes
import com.universalstreamhub.app.ui.screens.DetailScreen
import com.universalstreamhub.app.ui.screens.DiscoverScreen
import com.universalstreamhub.app.ui.screens.DownloadsScreen
import com.universalstreamhub.app.ui.screens.HomeScreen
import com.universalstreamhub.app.ui.screens.MyListScreen
import com.universalstreamhub.app.ui.screens.ProfileScreen
import com.universalstreamhub.app.ui.screens.SearchScreen
import com.universalstreamhub.app.ui.theme.HubAccent
import com.universalstreamhub.app.ui.theme.HubBlack
import com.universalstreamhub.app.ui.theme.HubMuted
import com.universalstreamhub.app.ui.theme.HubSurface

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Rounded.Home),
    Tab(Routes.DISCOVER, "Discover", Icons.Rounded.Explore),
    Tab(Routes.SEARCH, "Search", Icons.Rounded.Search),
    Tab(Routes.MY_LIST, "My List", Icons.Rounded.Bookmark),
    Tab(Routes.PROFILE, "Profile", Icons.Rounded.Person),
)

@Composable
fun HubApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBar = currentRoute == null || currentRoute in Routes.mainRoutes

    Scaffold(
        containerColor = HubBlack,
        bottomBar = {
            if (showBar) HubBottomBar(currentRoute = currentRoute, navController = navController)
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenDetail = { navController.navigate(Routes.detail(it)) },
                    onOpenSearch = { navController.navigateToTab(Routes.SEARCH) },
                    onOpenProfile = { navController.navigateToTab(Routes.PROFILE) },
                )
            }
            composable(Routes.DISCOVER) {
                DiscoverScreen(onOpenDetail = { navController.navigate(Routes.detail(it)) })
            }
            composable(Routes.SEARCH) {
                SearchScreen(onOpenDetail = { navController.navigate(Routes.detail(it)) })
            }
            composable(Routes.MY_LIST) {
                MyListScreen(
                    onOpenDetail = { navController.navigate(Routes.detail(it)) },
                    onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) },
                    onOpenProfile = { navController.navigateToTab(Routes.PROFILE) },
                )
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) },
                )
            }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("mediaId") { type = NavType.StringType }),
            ) { entry ->
                DetailScreen(
                    mediaId = entry.arguments?.getString("mediaId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenDetail = { navController.navigate(Routes.detail(it)) },
                )
            }
            composable(Routes.DOWNLOADS) {
                DownloadsScreen(
                    onBack = { navController.popBackStack() },
                    onManageStorage = { navController.navigateToTab(Routes.PROFILE) },
                )
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun HubBottomBar(currentRoute: String?, navController: NavHostController) {
    NavigationBar(containerColor = HubSurface, contentColor = HubMuted) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { navController.navigateToTab(tab.route) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = HubBlack,
                    selectedTextColor = HubBlack,
                    indicatorColor = HubAccent,
                    unselectedIconColor = HubMuted,
                    unselectedTextColor = HubMuted,
                ),
            )
        }
    }
}
