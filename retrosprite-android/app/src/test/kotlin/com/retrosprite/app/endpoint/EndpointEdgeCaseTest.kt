package com.retrosprite.app.endpoint

import com.retrosprite.app.endpoint.model.RetroArchResponse
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Edge-case coverage that complements [RetroArchEndpointServerTest]:
 *
 *  - oversized image payload (~5 MB Base64) must not crash the server
 *  - wrong `Content-Type` (e.g. `text/plain`) must still surface as a
 *    protocol-level error (HTTP 200 + `{"error": ...}`), never as a transport
 *    failure — RetroArch silently drops 4xx/5xx
 *  - completely empty body must not crash, must return error
 *  - duplicate query parameters: last-wins is fine, but no exception
 */
class EndpointEdgeCaseTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = false
        explicitNulls = false
    }

    @Test
    fun `large 5MB base64 image does not crash the endpoint`() = testApplication {
        val logger = RequestLogger()
        application { retroArchModule(PlaceholderResponseGenerator(), logger) }

        // ~5 MB of zero bytes → Base64 expands by 4/3 → ~6.7 MB of text.
        val rawSize = 5 * 1024 * 1024
        val bigBase64 = Base64.getEncoder().encodeToString(ByteArray(rawSize))

        // Build the payload by string concatenation rather than serialization to
        // avoid keeping multiple multi-megabyte copies in memory.
        val payload = buildString(bigBase64.length + 128) {
            append("""{"image":"""")
            append(bigBase64)
            append("""","label":"snes__big","state":{"paused":1}}""")
        }

        val resp = client.post("/?output=text") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        val parsed = json.decodeFromString(RetroArchResponse.serializer(), resp.bodyAsText())
        assertNotNull("a text response is still expected", parsed.text)

        val entry = logger.entries.value.first()
        assertEquals(rawSize, entry.imageBytes)
        assertTrue("paused flag preserved through large payload", entry.paused)
    }

    @Test
    fun `text plain content type still returns HTTP 200 with error payload`() = testApplication {
        val logger = RequestLogger()
        application { retroArchModule(PlaceholderResponseGenerator(), logger) }

        val resp = client.post("/?output=text") {
            contentType(ContentType.Text.Plain)
            setBody("""{"image":"x","label":"snes__game","state":{}}""")
        }

        // RetroArch never sees a 4xx — ContentNegotiation either rejects the
        // unsupported content type (415 by default) or the receive throws.
        // Either way the route handler catches it and returns 200 + error.
        assertEquals(
            "Protocol contract: even wrong content-type must surface as HTTP 200",
            HttpStatusCode.OK,
            resp.status,
        )
        val parsed = json.decodeFromString(RetroArchResponse.serializer(), resp.bodyAsText())
        assertNotNull("error must be populated for unsupported content type", parsed.error)

        val entry = logger.entries.value.first()
        assertNotNull("logger must record the content-type rejection", entry.errorMessage)
    }

    @Test
    fun `empty body returns protocol error not transport error`() = testApplication {
        val logger = RequestLogger()
        application { retroArchModule(PlaceholderResponseGenerator(), logger) }

        val resp = client.post("/?output=text") {
            contentType(ContentType.Application.Json)
            setBody("")
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        val parsed = json.decodeFromString(RetroArchResponse.serializer(), resp.bodyAsText())
        assertNotNull(parsed.error)
        assertEquals(1, logger.entries.value.size)
    }

    @Test
    fun `output query parameter values other than text still parse cleanly`() = testApplication {
        val logger = RequestLogger()
        application { retroArchModule(PlaceholderResponseGenerator(), logger) }

        // RetroArch can request "text|sound|image" depending on the AI Service preset.
        val resp = client.post("/?output=text%7Csound") {
            contentType(ContentType.Application.Json)
            setBody("""{"image":"x","label":"nes__contra","state":{}}""")
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        // The endpoint passes outputMode through verbatim — useful for downstream policy.
        assertEquals("text|sound", logger.entries.value.first().outputMode)
    }
}
