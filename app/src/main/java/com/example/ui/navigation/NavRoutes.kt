package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Main application navigation destination routes
 */
object NavRoutes {
    // Top-level destinations
    const val HOME = "home"
    const val LIBRARY = "library"
    const val AI = "ai?docId={docId}"
    const val TOOLS = "tools"
    const val SETTINGS = "settings"

    // Detail & feature screens
    const val READER = "reader/{docId}?page={page}"
    const val STUDY = "study/{docId}"
    const val SCANNER = "scanner"
    const val VAULT = "vault"
    const val ONBOARDING = "onboarding"

    // Route builders with type safety
    fun readerRoute(docId: Long, page: Int = 1): String = "reader/$docId?page=$page"
    fun aiRoute(docId: Long): String = "ai?docId=$docId"
    fun studyRoute(docId: Long): String = "study/$docId"
}

/**
 * Specification for top-level navigation items displayed on
 * Bottom Navigation Bar (Mobile) and Navigation Rail (Desktop/Tablet)
 */
sealed class NavigationItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    object Home : NavigationItem(
        route = NavRoutes.HOME,
        label = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_item_home"
    )

    object Library : NavigationItem(
        route = NavRoutes.LIBRARY,
        label = "Library",
        selectedIcon = Icons.Filled.Folder,
        unselectedIcon = Icons.Outlined.Folder,
        testTag = "nav_item_library"
    )

    object AI : NavigationItem(
        route = NavRoutes.AI,
        label = "AI",
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome,
        testTag = "nav_item_ai"
    )

    object Tools : NavigationItem(
        route = NavRoutes.TOOLS,
        label = "Tools",
        selectedIcon = Icons.Filled.Build,
        unselectedIcon = Icons.Outlined.Build,
        testTag = "nav_item_tools"
    )

    object Settings : NavigationItem(
        route = NavRoutes.SETTINGS,
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_item_settings"
    )

    companion object {
        val mainDestinations = listOf(Home, Library, AI, Tools, Settings)
    }
}
