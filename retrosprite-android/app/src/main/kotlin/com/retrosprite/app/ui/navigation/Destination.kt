package com.retrosprite.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Top-level destinations rendered by the bottom NavigationBar.
 * Order here is the order in the bar (left -> right).
 */
sealed class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Home : Destination(
        route = "home",
        label = "\u9996\u9875",
        icon = Icons.Filled.Home
    )

    data object Diagnostics : Destination(
        route = "diagnostics",
        label = "\u8bca\u65ad",
        icon = Icons.Filled.BugReport
    )

    data object Packs : Destination(
        route = "packs",
        label = "\u77e5\u8bc6\u5305",
        icon = Icons.Filled.Extension
    )

    data object Settings : Destination(
        route = "settings",
        label = "\u8bbe\u7f6e",
        icon = Icons.Filled.Settings
    )

    companion object {
        val ordered: List<Destination> = listOf(Home, Diagnostics, Packs, Settings)

        fun fromRoute(route: String?): Destination =
            ordered.firstOrNull { it.route == route } ?: Home
    }
}
