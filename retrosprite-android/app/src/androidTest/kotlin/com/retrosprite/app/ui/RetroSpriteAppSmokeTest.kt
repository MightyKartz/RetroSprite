package com.retrosprite.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.retrosprite.app.ui.theme.RetroSpriteTheme
import com.retrosprite.app.ui.viewmodel.ProvideUiDependencies
import com.retrosprite.app.ui.viewmodel.PreviewStub
import com.retrosprite.app.ui.viewmodel.UiDependencies
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test verifying that the root Scaffold renders all four tabs and switching
 * between them surfaces each screen's distinctive headline / placeholder.
 *
 * Uses [PreviewStub] dependencies so the test runs without a real endpoint or DataStore.
 */
@RunWith(AndroidJUnit4::class)
class RetroSpriteAppSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setRoot() {
        composeRule.setContent {
            RetroSpriteTheme {
                ProvideUiDependencies(
                    deps = UiDependencies(
                        endpoint = PreviewStub.endpoint(),
                        requestLog = PreviewStub.requestLog(),
                        settingsStore = PreviewStub.settings()
                    )
                ) {
                    RetroSpriteRoot()
                }
            }
        }
    }

    @Test
    fun bottomBarShowsAllFourTabs() {
        setRoot()
        composeRule.onNodeWithText("\u9996\u9875").assertIsDisplayed()
        composeRule.onNodeWithText("\u8bca\u65ad").assertIsDisplayed()
        composeRule.onNodeWithText("\u77e5\u8bc6\u5305").assertIsDisplayed()
        composeRule.onNodeWithText("\u8bbe\u7f6e").assertIsDisplayed()
    }

    @Test
    fun canNavigateThroughEveryTab() {
        setRoot()

        // Default Home tab: endpoint card title visible
        composeRule.onNodeWithText("\u672c\u673a\u7aef\u70b9".uppercase()).assertIsDisplayed()

        // Switch to Diagnostics
        composeRule.onNodeWithText("\u8bca\u65ad").performClick()
        composeRule.onNodeWithText("\u5feb\u901f\u8bca\u65ad".uppercase()).assertIsDisplayed()

        // Switch to Packs
        composeRule.onNodeWithText("\u77e5\u8bc6\u5305").performClick()
        composeRule.onNodeWithText("\u6e38\u620f\u77e5\u8bc6\u5305").assertIsDisplayed()

        // Switch to Settings
        composeRule.onNodeWithText("\u8bbe\u7f6e").performClick()
        composeRule.onNodeWithText("ENDPOINT").assertIsDisplayed()

        // Back to Home
        composeRule.onNodeWithText("\u9996\u9875").performClick()
        composeRule.onNodeWithText("\u672c\u673a\u7aef\u70b9".uppercase()).assertIsDisplayed()
    }
}
