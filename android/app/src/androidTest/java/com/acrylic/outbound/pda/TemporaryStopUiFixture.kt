package com.acrylic.outbound.pda

import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.security.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID

/** Replays the preserved signed stop response over its exact pre-stop Room checkpoint. */
internal class TemporaryStopUiFixture(private val app: PdaApplication) : AutoCloseable {
    val directory = File(app.filesDir, "d03-ui")
    val input = JSONObject(File(directory, "input.json").readText())
    val id: String = input.getString("clientOrderId")
    private val server = MockWebServer()
    private var stopped = false
    var stops = 0
        private set

    fun prepare() {
        check(BuildConfig.DEBUG && BuildConfig.TEMPORARY_CONTINUATION_DEVELOPMENT)
        check(InstrumentationRegistry.getArguments().getString("d03StopUi") == "true")
        check(android.os.Build.HARDWARE == "ranchu")
        server.dispatcher = dispatcher(); server.start(19499)
        app.session.settings.bind(server.url("/api/v1/").toString(), input.getJSONObject("session").getString("deviceId"), DeviceKeys().publicKey(true))
        val session = JSONObject(input.getJSONObject("session").toString()).put("signingKey", input.getJSONObject("signingKey"))
            .put("localSessionId", UUID.randomUUID().toString()).put("accessToken", "synthetic-ui-session")
            .put("timeAnchor", TrustedTime.anchor(Instant.now().toString(), TrustedTime.sample(app)))
        SessionVault(app).save(session)
    }

    private fun dispatcher() = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val route = "/api/v1/pda/temporary-orders/continuation-submissions/${input.getString("submissionId")}"
            val data = when {
                request.path == route && request.method == "GET" -> input.getJSONObject(if (stopped) "stopped" else "before")
                request.path == "$route/stop" && request.method == "POST" -> {
                    check(com.acrylic.outbound.pda.submission.SubmissionJson.canonical(JSONObject(request.body.readUtf8())) ==
                        com.acrylic.outbound.pda.submission.SubmissionJson.canonical(input.getJSONObject("request")))
                    stops++; stopped = true; input.getJSONObject("stopped")
                }
                else -> return MockResponse().setResponseCode(404).setBody("{\"code\":\"NOT_FOUND\",\"message\":\"UI replay only\"}")
            }
            return MockResponse().setHeader("Content-Type", "application/json").setHeader("Cache-Control", "no-store").setBody(JSONObject().put("code", "OK")
                .put("serviceMode", "NORMAL").put("serverRecoveryEpoch", "1").put("serverTime", Instant.now().toString())
                .put("data", data).toString())
        }
    }
    override fun close() { server.close() }
}
