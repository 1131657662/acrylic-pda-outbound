package com.acrylic.outbound.pda

import com.acrylic.outbound.pda.security.JwtVerifier
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.*
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/** Local test transport: forward real writes, optionally lose a committed renewal response. */
internal class RenewalProxy : AutoCloseable {
    private val server = MockWebServer()
    private val client = OkHttpClient.Builder().retryOnConnectionFailure(false).build()
    val exchanges = CopyOnWriteArrayList<JSONObject>()
    val loseResponse = AtomicBoolean(false)
    var afterRenewal: (() -> Unit)? = null
    val base: String get() = server.url("/api/v1/").toString()

    init {
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = forward(request)
        }
        server.start(18992)
    }

    private fun forward(request: RecordedRequest): MockResponse {
        val path = checkNotNull(request.path).removePrefix("/api/v1/")
        val bytes = request.body.readByteArray()
        val builder = Request.Builder().url("http://10.0.2.2:18092/api/v1/$path")
        request.headers.forEach { (key, value) ->
            if (key.lowercase() !in setOf("host", "connection", "content-length")) builder.header(key, value)
        }
        if (request.method != "GET") builder.method(checkNotNull(request.method),
            bytes.toRequestBody((request.getHeader("Content-Type") ?: "application/json").toMediaType()))
        return client.newCall(builder.build()).execute().use { response ->
            val body = checkNotNull(response.body).bytes()
            val result = MockResponse().setResponseCode(response.code).setBody(okio.Buffer().write(body))
                .setHeader("Content-Type", response.header("Content-Type") ?: "application/json")
            if (path.endsWith("/renewal")) record(JSONObject()
                .put("key", request.getHeader("Idempotency-Key")).put("body", String(bytes, Charsets.UTF_8))
                .put("status", response.code).put("response", JSONObject(String(body, Charsets.UTF_8)))
                .put("bodySha256", JwtVerifier.sha(bytes)), result)
            result
        }
    }

    private fun record(exchange: JSONObject, result: MockResponse) {
        exchanges += exchange
        afterRenewal?.also { afterRenewal = null }?.invoke()
        if (loseResponse.compareAndSet(true, false)) result.setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
    }

    override fun close() {
        server.shutdown()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
