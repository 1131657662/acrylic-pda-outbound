package com.acrylic.outbound.pda

import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.security.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

/** Exact signed FORMAL fault receipt replay on a copied frozen Room; synthetic emulator session. */
internal class FormalFaultUiFixture(val app: PdaApplication) : AutoCloseable {
    val directory = File(app.filesDir, "d07-formal-ui")
    val input = JSONObject(File(directory, "input.json").readText())
    val id = input.getString("submissionId")
    val assignment = input.getString("assignmentId")
    val calls = AtomicInteger()
    private val resultAvailable = AtomicBoolean()
    private val server = MockWebServer()
    private val vault = SessionVault(app)
    private lateinit var session: JSONObject

    fun prepare(loseFirst: Boolean) {
        check(BuildConfig.DEBUG && !BuildConfig.TEMPORARY_HANDOVER_DEVELOPMENT)
        check(!BuildConfig.TEMPORARY_CONTINUATION_DEVELOPMENT)
        check(InstrumentationRegistry.getArguments().getString("d07FormalUi") == "true")
        check(android.os.Build.HARDWARE == "ranchu")
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        val stage = checkNotNull(app.database.handoverStages().get(input.getString("caseId")))
        val manifest = JSONObject(stage.manifestJson)
        check(manifest.getString("stopKind") == "FAULT_FENCE" && !manifest.has("stopReceipt"))
        check(!manifest.getJSONObject("faultReceipt").getJSONObject("businessDecision").getBoolean("oldDeviceAcknowledged"))
        resultAvailable.set(!loseFirst)
        server.dispatcher = dispatcher(); server.start(19509)
        app.session.settings.bind(server.url("/api/v1/").toString(), input.getJSONObject("session").getString("deviceId"),
            DeviceKeys().publicKey(true))
        session = JSONObject(input.getJSONObject("session").toString()).put("localSessionId", UUID.randomUUID().toString())
            .put("accessToken", "synthetic-d07-formal-ui").put("timeAnchor", TrustedTime.anchor(Instant.now().toString(), TrustedTime.sample(app)))
        vault.save(session)
    }

    fun otherIdentity() { vault.save(JSONObject(session.toString()).put("userId", UUID.randomUUID().toString())) }
    fun restoreIdentity() { vault.save(session) }

    fun releaseOriginalResult() {
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        synchronized(app.submissions) { resultAvailable.set(true) }
        File(directory, "release-after-queries.txt").writeText(calls.get().toString())
    }

    private fun dispatcher() = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val route = "/api/v1/pda/submissions/$id"
            if (request.method != "GET" || request.path != route) return MockResponse().setResponseCode(404)
                .setBody("{\"code\":\"NOT_FOUND\",\"message\":\"isolated original-result replay only\"}")
            check(request.getHeader("Authorization") == "Bearer synthetic-d07-formal-ui")
            val count = calls.incrementAndGet()
            File(directory, "requests.tsv").appendText("GET\t$route\t$count\n")
            if (!resultAvailable.get()) return MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
            return MockResponse().setHeader("Content-Type", "application/json").setHeader("Cache-Control", "no-store")
                .setBody(input.getString("response"))
        }
    }

    override fun close() { vault.save(session); server.close() }
}
