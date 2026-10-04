package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.network.PdaNetwork
import com.acrylic.outbound.pda.preparation.ContractFixture
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File
import java.util.Base64

/** Replaces only the remote/session/time boundary; HTTP, signature checks, gzip and Room remain real. */
internal class HandoverDownloadFixture(directory: File) : AutoCloseable {
    val local = HandoverImportFixture(directory)
    val server = MockWebServer()
    private val cache = File(directory, "cache").apply { mkdirs() }
    private val gzip = ContractFixture.read().getJSONObject("gzip")
    var auth: JSONObject? = JSONObject(local.input.session.toString()).put("accessToken", "test-access")
    var clock = local.input.now
    var receipt = JSONObject(local.input.receipt.toString())
    var failPart: String? = null
    var corruptPart: String? = null
    var failAck = false
    var malformedAck = false
    var ackAssignment: String = local.target
    val ackBodies = java.util.concurrent.CopyOnWriteArrayList<String>()
    var afterResponse: (String) -> Unit = {}
    val connection: HandoverDownloadConnection

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path?.endsWith("/download-ack") == true) return acknowledge(request)
                return response(checkNotNull(request.path))
            }
        }
        val network = PdaNetwork(server.url("/api/v1/").toString())
        connection = object : HandoverDownloadConnection {
            override fun current() = auth
            override fun now() = clock
            override fun call(request: HandoverDownloadConnection.Call): JSONObject =
                network.request(request.path, request.body, headers(request.expected)).getJSONObject("data")
                    .also { afterResponse(request.path) }
            override fun download(request: HandoverDownloadConnection.Download) {
                network.download(request.path, request.target, headers(request.expected))
                afterResponse(request.path)
            }
        }
    }

    fun import(progress: (String) -> Unit = {}) = HandoverPreparationDownload(local.db, connection)
        .import(local.caseId, cache, progress)

    fun ack() = HandoverDownloadAcknowledgement(local.db, connection).acknowledge(local.caseId)

    fun assertNoImport() {
        assertNull(local.db.preparations().order(local.target))
        assertNull(local.db.work().order(local.target))
        assertTrue(local.db.captures().page(local.target, 0, 100).isEmpty())
        assertTrue(local.db.scans().page(local.target, 0, 100).isEmpty())
        assertTrue(local.db.work().events(local.target).isEmpty())
        assertEquals("STAGED", local.db.handoverStages().get(local.caseId)?.state)
        assertTrue(cache.listFiles().orEmpty().isEmpty())
        assertTrue(ackBodies.isEmpty())
    }

    private fun acknowledge(request: RecordedRequest): MockResponse {
        ackBodies.add(request.body.readUtf8())
        if (failAck) return MockResponse().setResponseCode(503).setBody("{}")
        if (malformedAck) return MockResponse().setHeader("Content-Type", "application/json").setBody("{")
        return json(JSONObject().put("assignmentId", ackAssignment).put("bundleId", receipt.getString("bundleId"))
            .put("downloadAckAt", local.input.now.plusSeconds(1).toString()))
    }

    private fun response(path: String): MockResponse {
        if (path == "/api/v1/pda/handovers/${local.caseId}/assignment") return json(local.input.assignment)
        if (path == "/api/v1/pda/assignments/${local.target}/preparation") return json(receipt)
        if (!path.startsWith("/api/v1/pda/assignments/${local.target}/content/")) return MockResponse().setResponseCode(404)
        val name = path.substringBefore('?').substringAfterLast('/')
        if (name == failPart) return MockResponse().setResponseCode(503).setHeader("Content-Type", "application/json")
            .setBody("{\"code\":\"TEST_DOWNLOAD_FAILED\",\"message\":\"test failure\"}")
        val bytes = Base64.getDecoder().decode(gzip.getString(name))
        if (name == corruptPart) bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        return MockResponse().setBody(okio.Buffer().write(bytes))
    }

    private fun json(data: JSONObject) = MockResponse().setHeader("Content-Type", "application/json")
        .setBody(JSONObject().put("code", "OK").put("serviceMode", "NORMAL").put("serverRecoveryEpoch", "1")
            .put("serverTime", local.input.now.toString()).put("data", data).toString())

    private fun headers(expected: JSONObject) = mapOf("Authorization" to "Bearer ${expected.getString("accessToken")}",
        "X-Server-Recovery-Epoch" to expected.getString("serverRecoveryEpoch"))

    override fun close() { server.close(); local.close() }
}
