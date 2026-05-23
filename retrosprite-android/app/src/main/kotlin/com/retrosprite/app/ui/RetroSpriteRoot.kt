package com.retrosprite.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.retrosprite.app.ui.navigation.Destination
import com.retrosprite.app.ui.navigation.RetroSpriteBottomBar
import com.retrosprite.app.ui.navigation.RetroSpriteNavHost
import com.retrosprite.app.ui.theme.RetroSpriteTheme

/**
 * Root composable: TopAppBar + NavHost + BottomNavigation.
 *
 * Provider injection happens *outside* of this composable in MainActivity (or in a
 * @Preview), via [com.retrosprite.app.ui.viewmodel.ProvideUiDependencies]. When no
 * dependencies are supplied, screens fall back to PreviewStub fakes so the app
 * still boots with believable mock data while Tasks #2 / #4 land.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RetroSpriteRoot() {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val current = Destination.fromRoute(currentRoute)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "RETROSPRITE",
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "/ ${current.label}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            bottomBar = {
                RetroSpriteBottomBar(navController = navController)
            }
        ) { innerPadding ->
            RetroSpriteNavHost(
                navController = navController,
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding()
                )
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 760)
@Composable
private fun RetroSpriteRootPreview() {
    RetroSpriteTheme {
        RetroSpriteRoot()
    }
}
