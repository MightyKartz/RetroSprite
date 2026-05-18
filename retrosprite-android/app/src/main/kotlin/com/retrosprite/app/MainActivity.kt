package com.retrosprite.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.retrosprite.app.ui.RetroSpriteRoot
import com.retrosprite.app.ui.theme.RetroSpriteTheme
import com.retrosprite.app.ui.viewmodel.ProvideUiDependencies

/**
 * Single Activity host for the RetroSprite Compose UI tree.
 *
 * The UI dependencies are supplied through [ProvideUiDependencies] from the
 * application-scoped [ServiceLocator] so every screen sees the real adapters
 * (Room-backed log, OkHttp health checks, DataStore-persisted settings)
 * instead of the PreviewStub fakes.
 *
 * `@Preview` composables that don't go through MainActivity continue to fall
 * back to the stub set baked into [com.retrosprite.app.ui.viewmodel.rememberUiDependencies].
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RetroSpriteTheme {
                ProvideUiDependencies(deps = ServiceLocator.uiDependencies) {
                    RetroSpriteRoot()
                }
            }
        }
    }
}
