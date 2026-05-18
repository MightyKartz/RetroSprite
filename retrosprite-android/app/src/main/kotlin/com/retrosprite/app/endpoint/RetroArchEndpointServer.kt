package com.retrosprite.app.endpoint

import android.util.Log
import com.retrosprite.app.endpoint.model.HealthResponse
import com.retrosprite.app.endpoint.model.RetroArchRequest
import com.retrosprite.app.endpoint.model.RetroArchResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local Ktor (CIO) server that implements the RetroArch AI Service protocol.
 *
 * Design constraints:
 *  - Binds **only** to `127.0.0.1` so the device cannot accept LAN traffic.
 *  - Returns HTTP **200** for every protocol-level error (per RetroArch quirks: the frontend
 *    treats 4xx/5xx as transport errors and silently drops the response, so we surface
 *    issues via `{ "error": "..." }` instead).
 *  - Response generation is delegated to [responseGenerator] so Task #5's `QueryPipeline`
 *    can replace [PlaceholderResponseGenerator] without touching this class.
 *
 * Lifecycle methods are idempotent and safe to call from any thread; [start] blocks until
 * the engine has bound the port.
 */
class RetroArchEndpointServer(
    private val port: Int = DEFAULT_PORT,
    private val host: String = DEFAULT_HOST,
    private val responseGenerator: ResponseGenerator = PlaceholderResponseGenerator(),
    private val requestLogger: RequestLogger = RequestLogger(),
) {

    private val running = AtomicBoolean(false)

    @Volatile
    private var engine: ApplicationEngine? = null

    val isRunning: Boolean get() = running.get()
    val boundPort: Int get() = port
    val logger: RequestLogger get() = requestLogger

    /** Starts the embedded engine. Throws if the port is already in use. */
    fun start() {
        if (!running.compareAndSet(false, true)) {
            Log.w(TAG, "start() called but server already running on $host:$port")
            return
        }
        try {
            engine = embeddedServer(CIO, host = host, port = port) {
                retroArchModule(responseGenerator, requestLogger)
            }.also { it.start(wait = false) }
            Log.i(TAG, "RetroArch endpoint listening on $host:$port")
        } catch (t: Throwable) {
            running.set(false)
            engine = null
            Log.e(TAG, "Failed to bind RetroArch endpoint on $host:$port", t)
            throw t
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        try {
            engine?.stop(gracePeriodMillis = 500, timeoutMillis = 1_500)
            Log.i(TAG, "RetroArch endpoint stopped")
        } catch (t: Throwable) {
            Log.w(TAG, "stop() encountered an error (ignored)", t)
        } finally {
            engine = null
        }
    }

    companion object {
        const val DEFAULT_HOST: String = "127.0.0.1"
        const val DEFAULT_PORT: Int = 8080
        private const val TAG = "RetroSprite/Endpoint"
    }
}

/**
 * Lenient JSON parser used by both production and tests:
 *  - `ignoreUnknownKeys`  → tolerate forward-compatible RetroArch additions.
 *  - `coerceInputValues`  → fall back to defaults for the wrong shape (e.g. `null` for an Int).
 *  - `encodeDefaults`     → omit `null` fields so the response stays minimal on the wire.
 */
@OptIn(ExperimentalSerializationApi::class)
internal val retroArchJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = false
    explicitNulls = false
}

/**
 * Installs the RetroArch routes on an [Application]. Exposed as an extension so unit tests
 * can mount the same module inside `testApplication { application { retroArchModule(...) } }`.
 */
fun Application.retroArchModule(
    responseGenerator: ResponseGenerator,
    requestLogger: RequestLogger,
) {
    install(ContentNegotiation) {
        json(retroArchJson)
    }
    routing {
        get("/health") {
            call.respond(HealthResponse(status = "ok", version = "0.1.0"))
        }

        post("/") {
            // 1. Query parameters — `output` is required by spec; default to "text" if absent.
            val outputMode = call.request.queryParameters["output"]?.takeIf { it.isNotBlank() }
                ?: "text"

            // 2. Body parsing wrapped in try/catch so RetroArch never sees a 4xx/5xx.
            val request: RetroArchRequest = try {
                call.receive()
            } catch (t: Throwable) {
                requestLogger.log(
                    label = "",
                    imageBase64 = "",
                    paused = false,
                    outputMode = outputMode,
                    responseText = "",
                    errorMessage = "malformed_request: ${t.message}",
                )
                call.respondJson(RetroArchResponse.error("Malformed request body"))
                return@post
            }

            // 3. Delegate to the generator; treat any failure as a protocol-level error.
            val response: RetroArchResponse = try {
                responseGenerator.generate(request, outputMode)
            } catch (t: Throwable) {
                Log.e("RetroSprite/Endpoint", "ResponseGenerator failed", t)
                requestLogger.log(
                    label = request.label,
                    imageBase64 = request.image,
                    paused = request.state.isPaused,
                    outputMode = outputMode,
                    responseText = "",
                    errorMessage = "generator_failed: ${t.message}",
                )
                call.respondJson(RetroArchResponse.error("Internal generator failure"))
                return@post
            }

            // 4. Record success path.
            requestLogger.log(
                label = request.label,
                imageBase64 = request.image,
                paused = request.state.isPaused,
                outputMode = outputMode,
                responseText = response.text.orEmpty(),
                errorMessage = response.error,
            )
            call.respondJson(response)
        }
    }
}

/** Helper that serializes via the lenient parser regardless of negotiated content type. */
private suspend fun io.ktor.server.application.ApplicationCall.respondJson(
    response: RetroArchResponse,
) {
    val body = retroArchJson.encodeToString(RetroArchResponse.serializer(), response)
    respondText(text = body, contentType = ContentType.Application.Json, status = HttpStatusCode.OK)
}
