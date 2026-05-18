package com.retrosprite.app.ui.integration

import android.content.Context
import com.retrosprite.app.endpoint.EndpointController
import com.retrosprite.app.endpoint.RetroArchEndpointServer
import com.retrosprite.app.ui.viewmodel.EndpointStatusProvider
import com.retrosprite.app.ui.viewmodel.UiEndpointPhase
import com.retrosprite.app.ui.viewmodel.UiEndpointStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Real [EndpointStatusProvider] backed by [EndpointController] + a tiny OkHttp
 * client that probes `/health`.
 *
 * Lifecycle:
 *  - On construction, starts a long-lived collector that mirrors
 *    `EndpointController.status` into the UI snapshot.
 *  - The provider does not own the [EndpointController] lifetime; it just
 *    observes and triggers control verbs through it. App shutdown
 *    cancels the supplied [scope] which tears down the collector.
 *
 * Health-check semantics:
 *  - Returns silently on success (the next `status` emission carries the
 *    `lastHealthCheckMillis` / `lastHealthOk` update).
 *  - On any IO failure, sets `lastHealthOk = false` but does NOT flip the
 *    phase — the engine may still be running, just unreachable from this
 *    process (e.g. firewall or socket glitch).
 */
class RealEndpointStatusProvider(
    private val context: Context,
    private val portFlow: Flow<Int>,
    private val scope: CoroutineScope,
    private val healthClient: OkHttpClient = defaultHealthClient(),
) : EndpointStatusProvider {

    @Volatile
    private var configuredPort: Int = RetroArchEndpointServer.DEFAULT_PORT

    private val _status = MutableStateFlow(initialUiStatus())
    override val status: StateFlow<UiEndpointStatus> = _status.asStateFlow()

    init {
        // Mirror the configured port from settings; used as the "fallback port"
        // when the server is Stopped/Starting/Error.
        scope.launch {
            portFlow.collect { port ->
                configuredPort = port
                _status.update { it.copy(port = if (it.phase == UiEndpointPhase.Running) it.port else port,
                    baseUrl = LOOPBACK_BASE_URL_TEMPLATE.format(if (it.phase == UiEndpointPhase.Running) it.port else port)) }
            }
        }

        // Mirror the endpoint's sealed status into UI shape.
        scope.launch {
            EndpointController.status.collect { ep ->
                _status.update { current ->
                    ep.toUi(
                        fallbackPort = configuredPort,
                        lastHealthCheckMillis = current.lastHealthCheckMillis,
                        lastHealthOk = current.lastHealthOk,
                    )
                }
            }
        }
    }

    override suspend fun restart() {
        EndpointController.stop(context)
        // Service shutdown is async; give the OS a beat to release the port.
        delay(POST_STOP_GRACE_MILLIS)
        EndpointController.start(context, configuredPort)
    }

    override suspend fun checkHealth() {
        val current = _status.value
        val port = if (current.phase == UiEndpointPhase.Running) current.port else configuredPort
        val ok = probeHealth(port)
        _status.update {
            it.copy(
                lastHealthCheckMillis = System.currentTimeMillis(),
                lastHealthOk = ok,
            )
        }
    }

    private suspend fun probeHealth(port: Int): Boolean = withContext(Dispatchers.IO) {
        val url = LOOPBACK_BASE_URL_TEMPLATE.format(port) + "/health"
        try {
            healthClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                response.isSuccessful
            }
        } catch (t: Throwable) {
            false
        }
    }

    private fun initialUiStatus(): UiEndpointStatus = EndpointController.status.value.toUi(
        fallbackPort = configuredPort,
    )

    companion object {
        private const val POST_STOP_GRACE_MILLIS: Long = 350L

        fun defaultHealthClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.SECONDS)
            .build()
    }
}
