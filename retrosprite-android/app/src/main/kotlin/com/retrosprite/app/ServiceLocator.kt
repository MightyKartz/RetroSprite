package com.retrosprite.app

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.retrosprite.app.data.db.DatabaseModule
import com.retrosprite.app.data.db.RetroSpriteDatabase
import com.retrosprite.app.data.gkp.BundledGkpImporter
import com.retrosprite.app.data.gkp.BundledGkpImportPhase
import com.retrosprite.app.data.gkp.BundledGkpImportStatus
import com.retrosprite.app.data.gkp.ExternalGkpInstaller
import com.retrosprite.app.data.repository.GameRepository
import com.retrosprite.app.data.repository.KnowledgeRepository
import com.retrosprite.app.data.repository.RequestLogRepository
import com.retrosprite.app.data.resolver.RepositoryGameResolver
import com.retrosprite.app.data.retrieval.LocalKnowledgeRetrievalPipeline
import com.retrosprite.app.domain.DefaultQueryPipeline
import com.retrosprite.app.domain.QueryPipeline
import com.retrosprite.app.domain.policy.AnswerComposer
import com.retrosprite.app.domain.policy.EvidenceAnswerPolicy
import com.retrosprite.app.endpoint.QueryPipelineResponseGenerator
import com.retrosprite.app.endpoint.InMemoryPendingQuestionStore
import com.retrosprite.app.endpoint.PendingQuestionResponseGenerator
import com.retrosprite.app.endpoint.PendingQuestionStore
import com.retrosprite.app.endpoint.RetroArchHotkeyListener
import com.retrosprite.app.endpoint.RoomBackedRequestLogSink
import com.retrosprite.app.endpoint.ResponseGenerator
import com.retrosprite.app.endpoint.HotkeyWakeResponseGenerator
import com.retrosprite.app.endpoint.model.DebugHotkeyVoiceOverlayResponse
import com.retrosprite.app.llm.DynamicLlmAdapter
import com.retrosprite.app.llm.LlmAdapter
import com.retrosprite.app.ui.overlay.AndroidHotkeyVoiceOverlayController
import com.retrosprite.app.ui.integration.RealEndpointStatusProvider
import com.retrosprite.app.ui.integration.RealGkpLibraryProvider
import com.retrosprite.app.ui.integration.RealGkpPreflightProvider
import com.retrosprite.app.ui.integration.RealLlmConfigTestProvider
import com.retrosprite.app.ui.integration.AndroidOverlayPermissionProvider
import com.retrosprite.app.ui.integration.RealPendingQuestionProvider
import com.retrosprite.app.ui.integration.RealPlayerQuestionProvider
import com.retrosprite.app.ui.integration.RealRequestLogProvider
import com.retrosprite.app.ui.integration.AndroidSpeechOutputProvider
import com.retrosprite.app.ui.integration.SherpaOnnxVoiceInputProvider
import com.retrosprite.app.ui.settings.toLlmConfigOrNull
import com.retrosprite.app.ui.settings.toDomainSpoilerLevel
import com.retrosprite.app.ui.settings.UiSettingsStore
import com.retrosprite.app.ui.viewmodel.EndpointStatusProvider
import com.retrosprite.app.ui.viewmodel.GkpLibraryProvider
import com.retrosprite.app.ui.viewmodel.GkpPreflightProvider
import com.retrosprite.app.ui.viewmodel.LlmConfigTestProvider
import com.retrosprite.app.ui.viewmodel.PendingQuestionProvider
import com.retrosprite.app.ui.viewmodel.OverlayPermissionProvider
import com.retrosprite.app.ui.viewmodel.PlayerQuestionProvider
import com.retrosprite.app.ui.viewmodel.RequestLogProvider
import com.retrosprite.app.ui.viewmodel.SettingsStore
import com.retrosprite.app.ui.viewmodel.SpeechOutputProvider
import com.retrosprite.app.ui.viewmodel.UiDependencies
import com.retrosprite.app.ui.viewmodel.UiSettings
import com.retrosprite.app.ui.viewmodel.VoiceInputProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Manual composition root for RetroSprite.
 *
 * The project intentionally avoids Hilt/Koin in Phase 0/1 — keeping the build
 * graph small and giving us a single, readable place to reason about object
 * lifetimes. Each property is a process-wide singleton constructed lazily on
 * first access; dependencies flow strictly downward
 * (`data → domain → endpoint → ui.integration`).
 *
 * Wiring order at app boot (see [RetroSpriteApp.onCreate]):
 *  1. [init] is invoked with the Application context.
 *  2. The first access to [endpointStatusProvider] / [requestLogProvider]
 *     triggers construction of the full graph.
 *  3. The application separately calls
 *     [com.retrosprite.app.endpoint.EndpointController.setRequestLogSink] +
 *     [com.retrosprite.app.endpoint.EndpointController.setResponseGenerator]
 *     before [com.retrosprite.app.endpoint.EndpointController.start].
 *
 * Tests can swap the singleton via [resetForTests] to keep state isolated
 * between cases.
 */
object ServiceLocator {

    @Volatile
    private var graph: Graph? = null

    /** Long-running scope tied to the application lifetime. */
    val applicationScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    fun init(context: Context) {
        if (graph != null) return
        synchronized(this) {
            if (graph != null) return
            graph = Graph(context.applicationContext)
        }
    }

    private fun requireGraph(): Graph =
        graph ?: error("ServiceLocator.init(context) must be called before use")

    // ---- Public surface ---------------------------------------------------

    val database: RetroSpriteDatabase get() = requireGraph().database
    val gameRepository: GameRepository get() = requireGraph().gameRepository
    val knowledgeRepository: KnowledgeRepository get() = requireGraph().knowledgeRepository
    val requestLogRepository: RequestLogRepository get() = requireGraph().requestLogRepository
    val llmAdapter: LlmAdapter get() = requireGraph().llmAdapter
    val queryPipeline: QueryPipeline get() = requireGraph().queryPipeline
    val responseGenerator: ResponseGenerator get() = requireGraph().responseGenerator
    val hotkeyVoiceOverlayController: RetroArchHotkeyListener
        get() = requireGraph().hotkeyVoiceOverlayController
    val hotkeyVoiceOverlayDebugProvider: () -> DebugHotkeyVoiceOverlayResponse
        get() = requireGraph().hotkeyVoiceOverlayController::debugSnapshot
    val requestLogSink: RoomBackedRequestLogSink get() = requireGraph().requestLogSink
    val settingsStore: SettingsStore get() = requireGraph().settingsStore

    val endpointStatusProvider: EndpointStatusProvider get() = requireGraph().endpointStatusProvider
    val requestLogProvider: RequestLogProvider get() = requireGraph().requestLogProvider
    val playerQuestionProvider: PlayerQuestionProvider get() = requireGraph().playerQuestionProvider
    val pendingQuestionProvider: PendingQuestionProvider get() = requireGraph().pendingQuestionProvider
    val gkpLibraryProvider: GkpLibraryProvider get() = requireGraph().gkpLibraryProvider
    val gkpPreflightProvider: GkpPreflightProvider get() = requireGraph().gkpPreflightProvider
    val overlayPermissionProvider: OverlayPermissionProvider get() = requireGraph().overlayPermissionProvider

    /** Bag passed to `ProvideUiDependencies` from MainActivity. */
    val uiDependencies: UiDependencies get() = requireGraph().uiDependencies

    /** Reactive port the endpoint should bind on. Mirrors the persisted setting. */
    val portState: StateFlow<Int> get() = requireGraph().portState

    /** Visible for tests only. */
    internal fun resetForTests() {
        synchronized(this) {
            graph = null
        }
    }

    // ---- Graph ------------------------------------------------------------

    private class Graph(private val appContext: Context) {

        // -- data layer --
        val database: RetroSpriteDatabase = DatabaseModule.provideDatabase(appContext)
        val gameRepository: GameRepository =
            DatabaseModule.provideGameRepository(appContext)
        val knowledgeRepository: KnowledgeRepository =
            DatabaseModule.provideKnowledgeRepository(appContext)
        val requestLogRepository: RequestLogRepository =
            DatabaseModule.provideRequestLogRepository(appContext)
        val bundledGkpImportStatus: MutableStateFlow<BundledGkpImportStatus> =
            MutableStateFlow(BundledGkpImportStatus())

        // -- UI settings (DataStore + Keystore-backed secrets) --
        val uiSettingsStore: UiSettingsStore = UiSettingsStore(appContext)
        val settingsStore: SettingsStore = uiSettingsStore

        val settingsState: StateFlow<UiSettings> = uiSettingsStore.settings
            .stateIn(
                scope = ServiceLocator.applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = UiSettings(),
            )

        init {
            ServiceLocator.applicationScope.launch {
                runCatching { uiSettingsStore.migrateLegacyApiKeyIfNeeded() }
                    .onFailure {
                        Log.w(TAG, "Failed to migrate legacy LLM API key; leaving real provider disabled", it)
                    }
            }
            ServiceLocator.applicationScope.launch {
                runCatching {
                    val finalStatus = BundledGkpImporter(
                        context = appContext,
                        gameRepository = gameRepository,
                        knowledgeRepository = knowledgeRepository,
                    ).importBundledPacks { status ->
                        bundledGkpImportStatus.value = status
                    }
                    if (finalStatus.failedPacks > 0) {
                        Log.w(TAG, "Bundled GKP import completed with failures: ${finalStatus.message}")
                    }
                }.onFailure {
                    bundledGkpImportStatus.value = BundledGkpImportStatus(
                        phase = BundledGkpImportPhase.Error,
                        message = it.message ?: "failed to import bundled GKP sample packs",
                        updatedAtMillis = System.currentTimeMillis(),
                    )
                    Log.w(TAG, "Failed to import bundled GKP sample packs", it)
                }
            }
        }

        // -- llm + domain layer --
        val llmAdapter: LlmAdapter = DynamicLlmAdapter(
            configProvider = { settingsState.value.toLlmConfigOrNull() },
        )

        val gameResolver = RepositoryGameResolver(gameRepository)

        val queryPipeline: QueryPipeline = DefaultQueryPipeline(
            resolver = gameResolver,
            retrieval = LocalKnowledgeRetrievalPipeline(knowledgeRepository),
            policy = EvidenceAnswerPolicy(),
            composer = AnswerComposer(
                maxTokensProvider = { settingsState.value.llmMaxTokens },
            ),
            llm = llmAdapter,
        )

        // -- endpoint adapters --
        val pendingQuestionStore: PendingQuestionStore = InMemoryPendingQuestionStore()

        val queryPipelineResponseGenerator: ResponseGenerator = QueryPipelineResponseGenerator(
            pipeline = queryPipeline,
            spoilerLevelProvider = { settingsState.value.spoilerLevel.toDomainSpoilerLevel() },
            gameResolver = gameResolver,
            knowledgeRepository = knowledgeRepository,
        )

        val responseGenerator: ResponseGenerator = PendingQuestionResponseGenerator(
            delegate = HotkeyWakeResponseGenerator(queryPipelineResponseGenerator),
            pendingQuestions = pendingQuestionStore,
        )

        val requestLogSink: RoomBackedRequestLogSink = RoomBackedRequestLogSink(
            repository = requestLogRepository,
            scope = ServiceLocator.applicationScope,
        )

        // Exposes the configured port as a StateFlow for the endpoint adapters
        // (avoids each adapter independently subscribing to DataStore).
        val portState: StateFlow<Int> = settingsState
            .map { it.port }
            .stateIn(
                scope = ServiceLocator.applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = 4_404,
            )

        // -- UI integration layer --
        val endpointStatusProvider: EndpointStatusProvider = RealEndpointStatusProvider(
            context = appContext,
            portFlow = portState,
            scope = ServiceLocator.applicationScope,
        )

        val requestLogProvider: RequestLogProvider = RealRequestLogProvider(
            repository = requestLogRepository,
            portProvider = { portState.value },
        )

        val playerQuestionProvider: PlayerQuestionProvider = RealPlayerQuestionProvider(
            responseGenerator = queryPipelineResponseGenerator,
        )

        val pendingQuestionProvider: PendingQuestionProvider = RealPendingQuestionProvider(
            store = pendingQuestionStore,
            settings = settingsState,
            scope = ServiceLocator.applicationScope,
        )

        val voiceInputProvider: VoiceInputProvider = SherpaOnnxVoiceInputProvider(
            context = appContext,
            scope = ServiceLocator.applicationScope,
        )

        val speechOutputProvider: SpeechOutputProvider = AndroidSpeechOutputProvider(appContext)

        val hotkeyVoiceOverlayController: AndroidHotkeyVoiceOverlayController =
            AndroidHotkeyVoiceOverlayController(
                context = appContext,
                voiceInput = voiceInputProvider,
                responseGenerator = queryPipelineResponseGenerator,
                speechOutput = speechOutputProvider,
                loggerProvider = { com.retrosprite.app.endpoint.EndpointController.requestLogger },
                showTranscriptHudProvider = {
                    settingsState.value.hotkeyVoiceTranscriptHudEnabled
                },
            )

        val overlayPermissionProvider: OverlayPermissionProvider =
            AndroidOverlayPermissionProvider(appContext)

        val llmConfigTestProvider: LlmConfigTestProvider = RealLlmConfigTestProvider()

        val gkpLibraryProvider: GkpLibraryProvider = RealGkpLibraryProvider(
            gameRepository = gameRepository,
            knowledgeRepository = knowledgeRepository,
            importStatus = bundledGkpImportStatus,
            scope = ServiceLocator.applicationScope,
            runInTransaction = { block -> database.withTransaction { block() } },
        )

        val externalGkpInstaller: ExternalGkpInstaller = ExternalGkpInstaller(
            gameRepository = gameRepository,
            knowledgeRepository = knowledgeRepository,
            runInTransaction = { block -> database.withTransaction { block() } },
        )

        val gkpPreflightProvider: GkpPreflightProvider = RealGkpPreflightProvider(
            context = appContext,
            installer = externalGkpInstaller,
        )

        val uiDependencies: UiDependencies = UiDependencies(
            endpoint = endpointStatusProvider,
            requestLog = requestLogProvider,
            settingsStore = settingsStore,
            playerQuestion = playerQuestionProvider,
            pendingQuestion = pendingQuestionProvider,
            voiceInput = voiceInputProvider,
            speechOutput = speechOutputProvider,
            overlayPermission = overlayPermissionProvider,
            llmConfigTest = llmConfigTestProvider,
            gkpLibrary = gkpLibraryProvider,
            gkpPreflight = gkpPreflightProvider,
        )

        private companion object {
            const val TAG = "ServiceLocator"
        }
    }
}
