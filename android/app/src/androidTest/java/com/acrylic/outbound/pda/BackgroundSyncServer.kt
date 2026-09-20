package com.acrylic.outbound.pda

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/** Synthetic loopback API for testing the real Android scheduler, never a business backend. */
internal class BackgroundSyncServer(private val throttled: Boolean = false) : AutoCloseable {
    val paths = CopyOnWriteArrayList<String>()
    val audits = CopyOnWriteArrayList<String>()
    private val server = MockWebServer()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = checkNotNull(request.path)
                paths += path
                if (throttled && path.contains("/sync/orders"))
                    return response(JSONObject(), "RATE_LIMITED", 429).setHeader("Retry-After", "30")
                if (path.contains("/sync/orders")) return response(page("ORDER", path))
                if (path.contains("/sync/barcodes")) return response(page("BARCODE", path))
                if (path.endsWith("/audit/events")) return audit(request)
                if (path.endsWith("/audit/archives")) return response(JSONObject(), "NOT_SUPPORTED", 404)
                if (path.endsWith("/auth/logout")) return response(JSONObject())
                return response(JSONObject(), "UNEXPECTED_TEST_REQUEST", 400)
            }
        }
        server.start(InetAddress.getByName("127.0.0.1"), 18991)
    }

    private fun audit(request: RecordedRequest): MockResponse {
        val body = JSONObject(request.body.readUtf8())
        val raw = SubmissionJson.canonical(body)
        audits += raw
        if (throttled) return MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
        val rows = body.getJSONArray("events")
        return response(JSONObject().put("recoveryEpoch", "1").put("originDeviceId", DEVICE)
            .put("acknowledgedThrough", rows.getJSONObject(rows.length() - 1).getString("localSeq"))
            .put("batchSha256", JwtVerifier.sha(raw.toByteArray())))
    }

    private fun page(stream: String, path: String): JSONObject = JSONObject()
        .put("streamType", stream).put("warehouseId", WAREHOUSE).put("recoveryEpoch", "1")
        .put("baselineId", if (path.contains("cursor=")) JSONObject.NULL else "background-test")
        .put("baselineComplete", true).put("baselineNextCursor", JSONObject.NULL)
        .put("highWatermark", "0").put("nextCursor", "cursor-$stream").put("hasMore", false)
        .put("baseline", JSONArray()).put("events", JSONArray())

    private fun response(data: JSONObject, code: String = "OK", status: Int = 200): MockResponse = MockResponse()
        .setResponseCode(status).setHeader("Content-Type", "application/json")
        .setBody(JSONObject().put("code", code).put("message", "synthetic scheduler test")
            .put("serverRecoveryEpoch", "1").put("serviceMode", "NORMAL")
            .put("serverTime", Instant.now().toString()).put("data", data).toString())

    override fun close() { server.shutdown() }

    companion object {
        const val WAREHOUSE = "b628cb88-c408-4c77-93bb-6e57e1377866"
        const val DEVICE = "d39d9c45-164e-43f4-b43d-0a915ed8e745"
        const val URL = "http://127.0.0.1:18991/api/v1/"
    }
}
