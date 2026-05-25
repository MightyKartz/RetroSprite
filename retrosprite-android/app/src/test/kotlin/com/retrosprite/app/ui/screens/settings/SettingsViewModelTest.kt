package com.retrosprite.app.ui.screens.settings

import com.retrosprite.app.ui.viewmodel.EndpointStatusProvider
import com.retrosprite.app.ui.viewmodel.LlmConfigTestProvider
import com.retrosprite.app.ui.viewmodel.OverlayPermissionProvider
import com.retrosprite.app.ui.viewmodel.SettingsStore
import com.retrosprite.app.ui.viewmodel.UiAboutInfo
import com.retrosprite.app.ui.viewmodel.UiEndpointPhase
import com.retrosprite.app.ui.viewmodel.UiEndpointStatus
import com.retrosprite.app.ui.viewmodel.UiLlmConfigTestResult
import com.retrosprite.app.ui.viewmodel.UiLlmProvider
import com.retrosprite.app.ui.viewmodel.UiOverlayPermissionState
import com.retrosprite.app.ui.viewmodel.UiSettings
import com.retrosprite.app.ui.viewmodel.UiSpoilerLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `refreshOverlayPermission delegates to provider and updates state`() = runTest(mainDispatcherRule.dispatcher) {
        val overlay = FakeOverlayPermissionProvider(
            UiOverlayPermissionState(isGranted = false),
        )
        val viewModel = viewModel(overlay)

        overlay.nextState = UiOverlayPermissionState(isGranted = true)
        viewModel.refreshOverlayPermission()
        advanceUntilIdle()

        assertEquals(true, viewModel.overlayPermissionState.value.isGranted)
        assertEquals(1, overlay.refreshCount)
    }

    @Test
    fun `openOverlayPermissionSettings delegates to provider`() = runTest(mainDispatcherRule.dispatcher) {
        val overlay = FakeOverlayPermissionProvider(
            UiOverlayPermissionState(isGranted = false),
        )
        val viewModel = viewModel(overlay)

        viewModel.openOverlayPermissionSettings()
        advanceUntilIdle()

        assertEquals(1, overlay.openSettingsCount)
    }

    @Test
    fun `applyHotkeyVoiceTranscriptHudEnabled delegates to settings store`() =
        runTest(mainDispatcherRule.dispatcher) {
            val store = FakeSettingsStore()
            val viewModel = SettingsViewModel(
                store = store,
                endpoint = FakeEndpointStatusProvider(),
                llmConfigTest = FakeLlmConfigTestProvider(),
                overlayPermission = FakeOverlayPermissionProvider(
                    UiOverlayPermissionState(isGranted = true),
                ),
                about = UiAboutInfo(),
            )

            viewModel.applyHotkeyVoiceTranscriptHudEnabled(false)
            advanceUntilIdle()

            assertEquals(false, store.state.value.hotkeyVoiceTranscriptHudEnabled)
        }

    private fun viewModel(
        overlay: OverlayPermissionProvider,
    ): SettingsViewModel =
        SettingsViewModel(
            store = FakeSettingsStore(),
            endpoint = FakeEndpointStatusProvider(),
            llmConfigTest = FakeLlmConfigTestProvider(),
            overlayPermission = overlay,
            about = UiAboutInfo(),
        )

    private class FakeOverlayPermissionProvider(
        initial: UiOverlayPermissionState,
    ) : OverlayPermissionProvider {
        override val state = MutableStateFlow(initial)
        var nextState: UiOverlayPermissionState = initial
        var refreshCount: Int = 0
        var openSettingsCount: Int = 0

        override suspend fun refresh() {
            refreshCount += 1
            state.value = nextState
        }

        override suspend fun openSettings() {
            openSettingsCount += 1
        }
    }

    private class FakeSettingsStore : SettingsStore {
        val state = MutableStateFlow(UiSettings())
        override val settings: Flow<UiSettings> = state

        override suspend fun updatePort(port: Int) {
            state.value = state.value.copy(port = port)
        }

        override suspend fun updateLlmConfig(
            provider: UiLlmProvider,
            apiKey: String,
            baseUrl: String,
            model: String,
            timeoutSeconds: Int,
            maxTokens: Int,
        ) {
            state.value = state.value.copy(
                llmProvider = provider,
                llmApiKey = apiKey,
                llmBaseUrl = baseUrl,
                llmModel = model,
                llmTimeoutSeconds = timeoutSeconds,
                llmMaxTokens = maxTokens,
            )
        }

        override suspend fun updateSpoilerLevel(level: UiSpoilerLevel) {
            state.value = state.value.copy(spoilerLevel = level)
        }

        override suspend fun updateHotkeyVoiceTranscriptHudEnabled(enabled: Boolean) {
            state.value = state.value.copy(hotkeyVoiceTranscriptHudEnabled = enabled)
        }
    }

    private class FakeEndpointStatusProvider : EndpointStatusProvider {
        override val status = MutableStateFlow(
            UiEndpointStatus(
                phase = UiEndpointPhase.Running,
                port = 4_404,
                baseUrl = "http://localhost:4404",
            )
        )

        override suspend fun restart() = Unit
        override suspend fun checkHealth() = Unit
    }

    private class FakeLlmConfigTestProvider : LlmConfigTestProvider {
        override suspend fun test(settings: UiSettings): UiLlmConfigTestResult =
            UiLlmConfigTestResult(
                ok = true,
                provider = settings.llmProvider.id,
                model = settings.llmModel,
                maxTokens = settings.llmMaxTokens,
                timeoutMs = settings.llmTimeoutSeconds * 1_000L,
            )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
