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

/** Exact signed HTTP response replay over its copied Room checkpoint, with synthetic emulator login. */
internal class TemporaryHandoverUiFixture(val app: PdaApplication) : AutoCloseable {
    val directory = File(app.filesDir, "d06-confirm-ui")
    val input = JSONObject(File(directory, "input.json").readText())
    val id = input.getString("caseId")
    val calls = AtomicInteger()
    private val server = MockWebServer()
    private val vault = SessionVault(app)
    private lateinit var session: JSONObject

    fun prepare() {
        check(BuildConfig.DEBUG && BuildConfig.TEMPORARY_HANDOVER_DEVELOPMENT)
        check(InstrumentationRegistry.getArguments().getString("d06ConfirmationUi") == "true")
        check(android.os.Build.HARDWARE == "ranchu")
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        val row = checkNotNull(app.database.temporaryHandoverConfirmations().get(id))
        server.dispatcher = dispatcher(JSONObject(row.boundCommandJson).getJSONObject("command"))
        server.start(19479)
        val key = DeviceKeys().publicKey(true)
        app.session.settings.bind(server.url("/api/v1/").toString(), input.getJSONObject("session").getString("deviceId"), key)
        session = JSONObject(input.getJSONObject("session").toString()).put("localSessionId", UUID.randomUUID().toString())
            .put("accessToken", "synthetic-d06-ui-session").put("timeAnchor", TrustedTime.anchor(Instant.now().toString(), TrustedTime.sample(app)))
        vault.save(session)
    }

    private fun dispatcher(command: JSONObject) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            if (input.has("activationManifest") && request.path == "/api/v1/pda/handovers/$id/manifest") {
                check(request.method == "GET")
                calls.incrementAndGet()
                return MockResponse().setHeader("Content-Type", "application/json").setHeader("Cache-Control", "no-store")
                    .setBody(input.getString("activationManifest"))
            }
            if (request.path != "/api/v1/pda/handovers/$id/temporary-confirmation") return MockResponse().setResponseCode(404)
                .setHeader("Content-Type", "application/json").setBody("{\"code\":\"NOT_FOUND\",\"message\":\"isolated UI replay only\"}")
            check(SubmissionJson.canonical(JSONObject(request.body.readUtf8())) == SubmissionJson.canonical(command))
            calls.incrementAndGet()
            return MockResponse().setHeader("Content-Type", "application/json").setHeader("Cache-Control", "no-store")
                .setBody(input.getString("receipt"))
        }
    }

    fun otherIdentity() { vault.save(JSONObject(session.toString()).put("userId", UUID.randomUUID().toString())) }
    override fun close() { vault.save(session); server.close() }
}
