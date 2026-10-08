package com.acrylic.outbound.pda

import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.security.*
import com.acrylic.outbound.pda.submission.SubmissionJson
import okhttp3.mockwebserver.*
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Replays actual isolated receiving STOP bytes over their pre-STOP Room checkpoint. */
internal class TemporaryHandoverStopUiFixture(private val app: PdaApplication) : AutoCloseable {
    val directory = File(app.filesDir, "d06-stop-ui")
    val input = JSONObject(File(directory, "input.json").readText())
    val id = input.getString("clientOrderId")
    val calls = AtomicInteger()
    private val server = MockWebServer()

    fun prepare() {
        check(BuildConfig.DEBUG && BuildConfig.TEMPORARY_HANDOVER_DEVELOPMENT)
        check(!BuildConfig.TEMPORARY_CONTINUATION_DEVELOPMENT)
        check(InstrumentationRegistry.getArguments().getString("d06StopUi") == "true")
        check(android.os.Build.HARDWARE == "ranchu")
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        server.dispatcher = dispatcher(); server.start(19499)
        app.session.settings.bind(server.url("/api/v1/").toString(), input.getJSONObject("session").getString("deviceId"),
            DeviceKeys().publicKey(true))
        val session = JSONObject(input.getJSONObject("session").toString()).put("localSessionId", UUID.randomUUID().toString())
            .put("accessToken", "synthetic-d06-stop-ui").put("timeAnchor", TrustedTime.anchor(Instant.now().toString(), TrustedTime.sample(app)))
        SessionVault(app).save(session)
    }

    private fun dispatcher() = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val route = "/api/v1/pda/temporary-orders/handover-submissions/${input.getString("submissionId")}"
            if (request.path == route && request.method == "GET") return MockResponse().setHeader("Content-Type", "application/json")
                .setHeader("Cache-Control", "no-store").setBody(input.getString(if (calls.get() == 0) "before" else "response"))
            if (request.path != "$route/stop" || request.method != "POST") return MockResponse().setResponseCode(404)
                .setBody("{\"code\":\"NOT_FOUND\",\"message\":\"isolated receiving STOP replay only\"}")
            check(SubmissionJson.canonical(JSONObject(request.body.readUtf8())) ==
                SubmissionJson.canonical(input.getJSONObject("request")))
            calls.incrementAndGet()
            return MockResponse().setHeader("Content-Type", "application/json").setHeader("Cache-Control", "no-store")
                .setBody(input.getString("response"))
        }
    }

    override fun close() { server.close() }
}
