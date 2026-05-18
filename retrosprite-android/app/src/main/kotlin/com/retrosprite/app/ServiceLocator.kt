package com.retrosprite.app

import android.content.Context
import com.retrosprite.app.data.db.DatabaseModule
import com.retrosprite.app.data.db.RetroSpriteDatabase
import com.retrosprite.app.data.repository.RequestLogRepository
import com.retrosprite.app.domain.DefaultQueryPipeline
import com.retrosprite.app.domain.QueryPipeline
import com.retrosprite.app.domain.policy.AnswerComposer
import com.retrosprite.app.domain.policy.FixedTextAnswerPolicy
import com.retrosprite.app.domain.resolver.LabelGameResolver
import com.retrosprite.app.domain.retrieval.NoOpRetrievalPipeline
import com.retrosprite.app.endpoint.QueryPipelineResponseGenerator
import com.retrosprite.app.endpoint.RoomBackedRequestLogSink
import com.retrosprite.app.endpoint.ResponseGenerator
import com.retrosprite.app.llm.LlmAdapter
import com.retrosprite.app.llm.LlmAdapterFactory
import com.retrosprite.app.llm.LlmConfig
import com.retrosprite.app.ui.integration.RealEndpointStatusProvider
import com.retrosprite.app.ui.integration.RealRequestLogProvider
import com.retrosprite.app.ui.settings.UiSettingsStore
import com.retrosprite.app.ui.viewmodel.EndpointStatusProvider
import com.retrosprite.app.ui.viewmodel.RequestLogProvider
import com.retrosprite.app.ui.viewmodel.SettingsStore
import com.retrosprite.app.ui.viewmodel.UiDependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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
    val requestLogRepository: RequestLogRepository get() = requireGraph().requestLogRepository
    val llmAdapter: LlmAdapter get() = requireGraph().llmAdapter
    val queryPipeline: QueryPipeline get() = requireGraph().queryPipeline
    val responseGenerator: ResponseGenerator get() = requireGraph().responseGenerator
    val requestLogSink: RoomBackedRequestLogSink get() = requireGraph().requestLogSink
    val settingsStore: SettingsStore get() = requireGraph().settingsStore

    val endpointStatusProvider: EndpointStatusProvider get() = requireGraph().endpointStatusProvider
    val requestLogProvider: RequestLogProvider get() = requireGraph().requestLogProvider

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
        val requestLogRepository: RequestLogRepository =
            DatabaseModule.provideRequestLogRepository(appContext)

        // -- llm + domain layer --
        // Phase 0: always Mock. Even when settings expose API key fields, the
        // factory will not switch providers until Phase 1.
        val llmAdapter: LlmAdapter = LlmAdapterFactory.create(LlmConfig.MOCK)

        val queryPipeline: QueryPipeline = DefaultQueryPipeline(
            resolver = LabelGameResolver(),
            retrieval = NoOpRetrievalPipeline(),
            policy = FixedTextAnswerPolicy(),
            composer = AnswerComposer(),
            llm = llmAdapter,
        )

        // -- endpoint adapters --
        val responseGenerator: ResponseGenerator = QueryPipelineResponseGenerator(queryPipeline)

        val requestLogSink: RoomBackedRequestLogSink = RoomBackedRequestLogSink(
            repository = requestLogRepository,
            scope = ServiceLocator.applicationScope,
        )

        // -- UI settings (DataStore) --
        val uiSettingsStore: UiSettingsStore = UiSettingsStore(appContext)
        val settingsStore: SettingsStore = uiSettingsStore

        // Exposes the configured port as a StateFlow for the endpoint adapters
        // (avoids each adapter independently subscribing to DataStore).
        val portState: StateFlow<Int> = uiSettingsStore.settings
            .map { it.port }
            .stateIn(
                scope = ServiceLocator.applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = 8080,
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

        val uiDependencies: UiDependencies = UiDependencies(
            endpoint = endpointStatusProvider,
            requestLog = requestLogProvider,
            settingsStore = settingsStore,
        )
    }
}
