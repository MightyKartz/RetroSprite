package com.retrosprite.app.endpoint

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sealed status surface consumed by the UI layer (Task #3 Home / Diagnostics screens).
 */
sealed interface EndpointStatus {
    data object Stopped : EndpointStatus
    data object Starting : EndpointStatus
    data class Running(val port: Int) : EndpointStatus
    data class Error(val message: String) : EndpointStatus
}

/**
 * Process-wide singleton that owns the [RetroArchEndpointServer] lifecycle and exposes its
 * state as a [StateFlow] for Compose observers.
 *
 * Two entry-points:
 *  - [start] from the [android.app.Application]: tells the OS to spin up [EndpointService],
 *    which then calls [bindToService] internally.
 *  - [bindToService] / [unbindFromService] are wired by [EndpointService] only and must not
 *    be called from UI code.
 *
 * UI / Domain layers should treat this object as read-only and call [start] / [stop] for
 * control. The [RequestLogger] is exposed via [requestLogger] for the Diagnostics screen.
 */
object EndpointController {

    private const val TAG = "RetroSprite/Endpoint"

    private val _status = MutableStateFlow<EndpointStatus>(EndpointStatus.Stopped)
    val status: StateFlow<EndpointStatus> = _status.asStateFlow()

    @Volatile
    private var server: RetroArchEndpointServer? = null

    @Volatile
    private var loggerInstance: RequestLogger = RequestLogger()

    /** Surface for UI to render request history. Stable across server restarts. */
    val requestLogger: RequestLogger get() = loggerInstance

    /** Controls which generator the next server start will use. Task #5 swaps this. */
    @Volatile
    private var responseGenerator: ResponseGenerator = PlaceholderResponseGenerator()

    /**
     * Replace the default in-memory [RequestLogSink] with a Room-backed sink (or any
     * other adapter). Must be called BEFORE [start] / [bindToService] so the next
     * server instance picks up the swapped logger; calling it while the server is
     * already running has no effect on in-flight requests.
     *
     * The integration layer (see `RetroSpriteApp.onCreate`) uses this to plug in
     * [com.retrosprite.app.endpoint.RoomBackedRequestLogSink] without breaking
     * Bill's [RequestLogSink] abstraction.
     */
    fun setRequestLogSink(sink: RequestLogSink) {
        loggerInstance = RequestLogger(sink)
    }

    /**
     * Replace the [ResponseGenerator] used by the next server start. Must be called
     * BEFORE [start] / [bindToService]; an already-bound server keeps the generator
     * captured at construction time.
     */
    fun setResponseGenerator(generator: ResponseGenerator) {
        responseGenerator = generator
    }

    /**
     * Convenience entry-point invoked from [com.retrosprite.app.RetroSpriteApp.onCreate].
     * Idempotent: if the service is already running, this is a no-op.
     */
    fun start(context: Context, port: Int = RetroArchEndpointServer.DEFAULT_PORT) {
        if (_status.value is EndpointStatus.Running || _status.value is EndpointStatus.Starting) {
            return
        }
        _status.value = EndpointStatus.Starting
        try {
            EndpointService.start(context.applicationContext, port)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start EndpointService", t)
            _status.value = EndpointStatus.Error(t.message ?: "unknown")
        }
    }

    /** Stops the foreground service and the underlying server. */
    fun stop(context: Context) {
        EndpointService.stop(context.applicationContext)
    }

    // -- Internal: invoked by EndpointService -------------------------------------------

    internal fun bindToService(context: Context, port: Int) {
        if (server != null) {
            _status.value = EndpointStatus.Running(port)
            return
        }
        try {
            val instance = RetroArchEndpointServer(
                port = port,
                responseGenerator = responseGenerator,
                requestLogger = loggerInstance,
            ).also { it.start() }
            server = instance
            _status.value = EndpointStatus.Running(port)
        } catch (t: Throwable) {
            _status.value = EndpointStatus.Error(t.message ?: "bind_failed")
            throw t
        }
    }

    internal fun unbindFromService() {
        try {
            server?.stop()
        } finally {
            server = null
            _status.value = EndpointStatus.Stopped
        }
    }
}
