package com.retrosprite.app.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.retrosprite.app.ui.screens.diagnostics.DiagnosticsScreen
import com.retrosprite.app.ui.screens.home.HomeScreen
import com.retrosprite.app.ui.screens.packs.PacksScreen
import com.retrosprite.app.ui.screens.settings.SettingsScreen

@Composable
fun RetroSpriteNavHost(
    navController: NavHostController,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Destination.Home.route,
        modifier = modifier
    ) {
        composable(Destination.Home.route) { HomeScreen(contentPadding = contentPadding) }
        composable(Destination.Diagnostics.route) { DiagnosticsScreen(contentPadding = contentPadding) }
        composable(Destination.Packs.route) { PacksScreen(contentPadding = contentPadding) }
        composable(Destination.Settings.route) { SettingsScreen(contentPadding = contentPadding) }
    }
}
