package com.acrylic.outbound.pda

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.*
import java.util.concurrent.atomic.AtomicBoolean

/** Forwards to the real backend and can lose exactly one response after its transaction commits. */
class SubmissionProxy(private val upstream: String, port: Int = 0) : AutoCloseable {
    private val server = MockWebServer()
    private val client = OkHttpClient.Builder().retryOnConnectionFailure(false).build()
    val loseSubmissionResponse = AtomicBoolean(false)
    val loseStopResponse = AtomicBoolean(false)
    val loseContinuationResponse = AtomicBoolean(false)
    val losePairingCreateResponse = AtomicBoolean(false)
    val losePairingConfirmResponse = AtomicBoolean(false)
    val offlinePairingChallenge = AtomicBoolean(false)
    val base: String get() = server.url("/api/v1/").toString()
    init {
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = checkNotNull(request.path).removePrefix("/api/v1/")
                if (path == "pda/pairing/challenges" && offlinePairingChallenge.compareAndSet(true, false))
                    return MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                val builder = Request.Builder().url(upstream + path)
                request.headers.forEach { (key, value) -> if (key.lowercase() !in setOf("host", "connection", "content-length")) builder.header(key, value) }
                if (request.method != "GET") builder.method(checkNotNull(request.method), request.body.readByteArray().toRequestBody((request.getHeader("Content-Type") ?: "application/json").toMediaType()))
                return client.newCall(builder.build()).execute().use { response ->
                    val result = MockResponse().setResponseCode(response.code).setBody(okio.Buffer().write(response.body!!.bytes()))
                    result.setHeader("Content-Type", response.header("Content-Type") ?: "application/json")
                    if (path.endsWith("/submissions") && loseSubmissionResponse.compareAndSet(true, false)) result.setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                    if (path.endsWith("/stop-ack") && loseStopResponse.compareAndSet(true, false)) result.setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                    if (path.startsWith("pda/continuations/") && path.endsWith("/activate") && loseContinuationResponse.compareAndSet(true, false)) result.setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                    if (path == "pda/pairing/applications" && losePairingCreateResponse.compareAndSet(true, false)) result.setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                    if (path == "pda/pairing/confirm" && losePairingConfirmResponse.compareAndSet(true, false)) result.setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                    result
                }
            }
        }
        server.start(port)
    }
    override fun close() { server.shutdown(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
}
