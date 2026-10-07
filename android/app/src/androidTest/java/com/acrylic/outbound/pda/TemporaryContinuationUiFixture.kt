package com.acrylic.outbound.pda

import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.security.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID

/** Replays the actual signed HTTP acknowledgement over the matching copied Room checkpoint. */
internal class TemporaryContinuationUiFixture(val app: PdaApplication) : AutoCloseable {
    val directory = File(app.filesDir, "d02-ui").apply { mkdirs() }
    val input = JSONObject(File(directory, "input.json").readText())
    val id: String = input.getString("clientOrderId")
    private val server = MockWebServer()
    private val vault = SessionVault(app)
    lateinit var session: JSONObject
        private set

    fun prepare() {
        check(BuildConfig.DEBUG && BuildConfig.TEMPORARY_CONTINUATION_DEVELOPMENT)
        check(InstrumentationRegistry.getArguments().getString("d02ContinuationUi") == "true")
        check(android.os.Build.HARDWARE == "ranchu")
        val link = checkNotNull(app.database.temporaryContinuations().forTarget(id))
        val prepared = checkNotNull(app.database.temporary().prepared(link.ticketId))
        server.dispatcher = dispatcher(prepared.ackBodyJson)
        server.start(19479)
        val key = DeviceKeys().publicKey(true)
        app.session.settings.bind(server.url("/api/v1/").toString(), input.getJSONObject("session").getString("deviceId"), key)
        session = JSONObject(input.getJSONObject("session").toString()).put("signingKey", input.getJSONObject("signingKey"))
            .put("localSessionId", UUID.randomUUID().toString()).put("accessToken", "synthetic-ui-session")
            .put("timeAnchor", TrustedTime.anchor(Instant.now().toString(), TrustedTime.sample(app)))
        vault.save(session)
    }

    private fun dispatcher(body: String) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            if (request.path?.endsWith("/download-ack") != true) return MockResponse().setResponseCode(404)
                .setHeader("Content-Type", "application/json").setBody("{\"code\":\"NOT_FOUND\",\"message\":\"isolated UI replay only\"}")
            check(JSONObject(request.body.readUtf8()).toString() == JSONObject(body).toString())
            val response = JSONObject().put("code", "OK").put("serviceMode", "NORMAL").put("serverRecoveryEpoch", "1")
                .put("serverTime", Instant.now().toString()).put("data", input.getJSONObject("ack"))
            return MockResponse().setHeader("Content-Type", "application/json").setHeader("Cache-Control", "no-store").setBody(response.toString())
        }
    }

    fun otherIdentity() { vault.save(JSONObject(session.toString()).put("userId", UUID.randomUUID().toString())) }
    fun restoreIdentity() { vault.save(session) }
    override fun close() { restoreIdentity(); server.close() }
}
