package com.acrylic.outbound.pda

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.*
import java.util.concurrent.atomic.AtomicBoolean

/** Forwards to the real backend and can lose exactly one response after its transaction commits. */
class SubmissionProxy(private val upstream: String) : AutoCloseable {
    private val server = MockWebServer()
    private val client = OkHttpClient.Builder().retryOnConnectionFailure(false).build()
    val loseSubmissionResponse = AtomicBoolean(false)
    val base: String get() = server.url("/api/v1/").toString()
    init {
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = checkNotNull(request.path).removePrefix("/api/v1/")
                val builder = Request.Builder().url(upstream + path)
                request.headers.forEach { (key, value) -> if (key.lowercase() !in setOf("host", "connection", "content-length")) builder.header(key, value) }
                if (request.method != "GET") builder.method(checkNotNull(request.method), request.body.readByteArray().toRequestBody((request.getHeader("Content-Type") ?: "application/json").toMediaType()))
                return client.newCall(builder.build()).execute().use { response ->
                    val result = MockResponse().setResponseCode(response.code).setBody(okio.Buffer().write(response.body!!.bytes()))
                    result.setHeader("Content-Type", response.header("Content-Type") ?: "application/json")
                    if (path.endsWith("/submissions") && loseSubmissionResponse.compareAndSet(true, false)) result.setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                    result
                }
            }
        }
        server.start()
    }
    override fun close() { server.shutdown(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
}
