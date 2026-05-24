package com.retrosprite.app

import android.app.Application
import com.retrosprite.app.endpoint.EndpointController

/**
 * Custom [Application] subclass for RetroSprite.
 *
 * Acts as the composition root for app-wide singletons. Wiring order is
 * intentional and load-bearing:
 *
 *  1. [ServiceLocator.init] builds the data → domain → endpoint object graph.
 *  2. [EndpointController.setRequestLogSink] swaps the default in-memory sink
 *     for the Room-backed adapter — must happen before [EndpointController.start]
 *     so the first request is already persisted.
 *  3. [EndpointController.setResponseGenerator] swaps the
 *     `PlaceholderResponseGenerator` for the real `QueryPipelineResponseGenerator`
 *     that delegates into the domain pipeline.
 *  4. [EndpointController.start] spins up the foreground service which in turn
 *     binds the Ktor engine on the configured port.
 *
 * If any step throws, the foreground service is not started and the UI falls
 * back to the existing PreviewStub experience (no crash).
 */
class RetroSpriteApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Build the singleton object graph.
        ServiceLocator.init(this)

        // 2-3. Replace endpoint defaults with real implementations BEFORE start.
        EndpointController.setRequestLogSink(ServiceLocator.requestLogSink)
        EndpointController.setResponseGenerator(ServiceLocator.responseGenerator)
        EndpointController.setHotkeyListener(ServiceLocator.hotkeyVoiceOverlayController)
        EndpointController.setHotkeyVoiceOverlayDebugProvider(
            ServiceLocator.hotkeyVoiceOverlayDebugProvider,
        )

        // 4. Boot the local RetroArch endpoint as a foreground service.
        // TODO(Phase 1): observe ServiceLocator.portState and restart the
        // endpoint when the user changes the port from Settings. For Phase 0
        // we capture the value once at boot.
        EndpointController.start(applicationContext, ServiceLocator.portState.value)
    }
}
