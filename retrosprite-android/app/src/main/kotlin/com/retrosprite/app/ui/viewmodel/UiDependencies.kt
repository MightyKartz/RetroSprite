package com.retrosprite.app.ui.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Bag of UI dependencies, injected from the Application/Activity layer.
 *
 * Phase 0: defaults to PreviewStub implementations so the app boots with believable
 * mock data even before Tasks #2 / #4 wire real services.
 *
 * Future: in [com.retrosprite.app.RetroSpriteApp.onCreate] (or via a
 * `rememberAppDependencies()` factory backed by Application), build a real instance:
 *
 * ```
 * setContent {
 *   RetroSpriteTheme {
 *     ProvideUiDependencies(
 *       deps = UiDependencies(
 *         endpoint = realEndpointAdapter,
 *         requestLog = realLogAdapter,
 *         settingsStore = realDataStoreAdapter
 *       )
 *     ) { RetroSpriteRoot() }
 *   }
 * }
 * ```
 */
data class UiDependencies(
    val endpoint: EndpointStatusProvider,
    val requestLog: RequestLogProvider,
    val settingsStore: SettingsStore,
    val playerQuestion: PlayerQuestionProvider = PreviewStub.playerQuestion(),
    val pendingQuestion: PendingQuestionProvider = PreviewStub.pendingQuestion(),
    val voiceInput: VoiceInputProvider = PreviewStub.voiceInput(),
    val speechOutput: SpeechOutputProvider = PreviewStub.speechOutput(),
    val overlayPermission: OverlayPermissionProvider = PreviewStub.overlayPermission(),
    val llmConfigTest: LlmConfigTestProvider = PreviewStub.llmConfigTest(),
    val gkpLibrary: GkpLibraryProvider = PreviewStub.gkpLibrary(),
    val gkpPreflight: GkpPreflightProvider = PreviewStub.gkpPreflight(),
    val about: UiAboutInfo = UiAboutInfo()
)

private val LocalUiDependencies: ProvidableCompositionLocal<UiDependencies?> =
    staticCompositionLocalOf { null }

/**
 * Wrap your composable tree with this to inject dependencies. If never called,
 * downstream consumers fall back to in-memory PreviewStub implementations so
 * @Preview tooling and ad-hoc launches still render meaningful UI.
 */
@Composable
fun ProvideUiDependencies(
    deps: UiDependencies,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalUiDependencies provides deps, content = content)
}

/**
 * Resolves the active [UiDependencies], lazily constructing a stub set on first
 * access if none has been provided. The stub is held in a local-scoped cache so
 * the ViewModels in a single composition share the same fake.
 */
@Composable
fun rememberUiDependencies(): UiDependencies {
    val provided = LocalUiDependencies.current
    if (provided != null) return provided
    return DefaultStubDependencies
}

private val DefaultStubDependencies: UiDependencies by lazy {
    UiDependencies(
        endpoint = PreviewStub.endpoint(),
        requestLog = PreviewStub.requestLog(),
        settingsStore = PreviewStub.settings(),
        playerQuestion = PreviewStub.playerQuestion(),
        pendingQuestion = PreviewStub.pendingQuestion(),
        voiceInput = PreviewStub.voiceInput(),
        speechOutput = PreviewStub.speechOutput(),
        overlayPermission = PreviewStub.overlayPermission(),
        llmConfigTest = PreviewStub.llmConfigTest(),
        gkpLibrary = PreviewStub.gkpLibrary(),
        gkpPreflight = PreviewStub.gkpPreflight(),
    )
}
