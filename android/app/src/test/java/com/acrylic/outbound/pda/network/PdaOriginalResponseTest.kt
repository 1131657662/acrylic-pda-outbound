package com.acrylic.outbound.pda.network

import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class PdaOriginalResponseTest {
    @Test fun rawUploadKeepsOriginalBytesAndExactResponse() {
        MockWebServer().use { server ->
            val raw = byteArrayOf(0, 1, 127, -1); val response = "{\"status\":\"RECEIVED\"}".toByteArray()
            server.enqueue(json(response)); val network = PdaNetwork(server.url("/api/v1/").toString())
            assertArrayEquals(response, network.original("pda/local-draft-evidence/item", null, emptyMap(), raw))
            val request = server.takeRequest(); assertEquals("application/octet-stream", request.getHeader("Content-Type"))
            assertArrayEquals(raw, request.body.readByteArray())
            assertThrows(IllegalArgumentException::class.java) { network.original("pda/item", raw, emptyMap(), raw) }
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun acceptsNoStoreAmongStandardDirectivesButRejectsItsAbsence() {
        MockWebServer().use { server ->
            val expected = "{\"code\":\"OK\"}".toByteArray()
            server.enqueue(json(expected).setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"))
            server.enqueue(json(expected).setHeader("Cache-Control", "no-cache, max-age=0, must-revalidate"))
            val network = PdaNetwork(server.url("/api/v1/").toString())
            assertArrayEquals(expected, network.original("pda/submissions/original", null, emptyMap()))
            assertThrows(IllegalStateException::class.java) { network.original("pda/submissions/original", null, emptyMap()) }
        }
    }
    @Test fun returnsExactUtf8WithoutWrappingOrNormalizingTheSignedDocument() {
        MockWebServer().use { server ->
            val expected = "{\"签名\":\"abc\",\"status\":\"PARTIAL\"}".toByteArray(Charsets.UTF_8)
            server.enqueue(json(expected))
            val network = PdaNetwork(server.url("/api/v1/").toString())
            val request = "{\"operation\":\"OPEN\"}".toByteArray()
            assertArrayEquals(expected, network.original("pda/temporary-source-evidence/open", request, emptyMap()))
            assertArrayEquals(request, server.takeRequest().body.readByteArray())
        }
    }

    @Test fun droppedPostIsNotAutomaticallyRetried() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            val network = PdaNetwork(server.url("/api/v1/").toString())
            assertThrows(IOException::class.java) { network.original("pda/temporary-source-evidence/open", "{}".toByteArray(), emptyMap()) }
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun errorEnvelopeStillObservesRecoveryFence() {
        MockWebServer().use { server ->
            server.enqueue(json("{\"code\":\"SERVER_RECOVERY_REQUIRED\",\"serviceMode\":\"RESTORING\"}".toByteArray()).setResponseCode(503))
            var observed: JSONObject? = null
            val network = PdaNetwork(server.url("/api/v1/").toString(), observe = { observed = it })
            val error = assertThrows(ApiFailure::class.java) { network.original("pda/receipts/key", null, emptyMap()) }
            assertEquals(503, error.httpStatus); assertEquals("RESTORING", observed?.getString("serviceMode"))
        }
    }

    @Test fun oversizedOrHtmlResponsesCannotBeAcceptedAsOriginalReceipts() {
        MockWebServer().use { server ->
            server.enqueue(json(ByteArray(262145) { 32 }))
            server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("login"))
            val network = PdaNetwork(server.url("/api/v1/").toString())
            repeat(2) { assertThrows(IllegalStateException::class.java) { network.original("pda/receipts/key", null, emptyMap()) } }
        }
    }

    private fun json(bytes: ByteArray) = MockResponse().setHeader("Content-Type", "application/json")
        .setHeader("Cache-Control", "no-store").setBody(okio.Buffer().write(bytes))
}
