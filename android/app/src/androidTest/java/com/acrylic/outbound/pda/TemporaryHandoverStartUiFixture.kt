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

/** Actual signed ACK replay with synthetic emulator login over an unchanged activation checkpoint. */
internal class TemporaryHandoverStartUiFixture(val app: PdaApplication) : AutoCloseable {
    val directory = File(app.filesDir, "d06-start-ui")
    private val input = JSONObject(File(directory, "input.json").readText())
    val caseId = input.getString("caseId")
    val activation = checkNotNull(app.database.temporaryHandoverActivations().get(caseId))
    val client = activation.newClientOrderId
    val calls = AtomicInteger()
    private val server = MockWebServer()
    private val vault = SessionVault(app)
    private lateinit var session: JSONObject
    private val original = app.database.temporaryHandoverStages().get(caseId)!!.payloadBytes
    private val guards = app.database.temporaryHandoverGuards().forCase(caseId)

    fun prepare() {
        check(BuildConfig.DEBUG && BuildConfig.TEMPORARY_HANDOVER_DEVELOPMENT)
        check(InstrumentationRegistry.getArguments().getString("d06StartUi") == "true")
        check(android.os.Build.HARDWARE == "ranchu")
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        server.dispatcher = dispatcher()
        server.start(19479)
        app.session.settings.bind(server.url("/api/v1/").toString(), input.getJSONObject("session").getString("deviceId"),
            DeviceKeys().publicKey(true))
        session = JSONObject(input.getJSONObject("session").toString()).put("localSessionId", UUID.randomUUID().toString())
            .put("accessToken", "synthetic-d06-start-ui")
            .put("timeAnchor", TrustedTime.anchor(Instant.now().toString(), TrustedTime.sample(app)))
        vault.save(session)
    }

    private fun dispatcher() = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val row = app.database.temporary().prepared(activation.ticketId)!!
            if (request.path != "/api/v1/pda/temporary-preparations/${row.authorizationId}/download-ack")
                return MockResponse().setResponseCode(404)
            check(request.method == "POST" && request.getHeader("Idempotency-Key") == row.ackKey)
            check(SubmissionJson.canonical(JSONObject(request.body.readUtf8())) == SubmissionJson.canonical(JSONObject(row.ackBodyJson)))
            File(directory, "ack-key.txt").writeText(row.ackKey)
            if (calls.incrementAndGet() == 1) return MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
            val response = JSONObject().put("code", "OK").put("message", "isolated replay")
                .put("serviceMode", "NORMAL").put("serverRecoveryEpoch", session.getString("serverRecoveryEpoch"))
                .put("serverTime", Instant.now().toString())
                .put("data", JSONObject(input.getString("ackData")))
            return MockResponse().setHeader("Content-Type", "application/json").setHeader("Cache-Control", "no-store")
                .setBody(response.toString())
        }
    }

    fun preserve() {
        check(original.contentEquals(app.database.temporaryHandoverStages().get(caseId)!!.payloadBytes))
        check(activation.receiptBytes.contentEquals(app.database.temporaryHandoverActivations().get(caseId)!!.receiptBytes))
        check(guards == app.database.temporaryHandoverGuards().forCase(caseId))
    }

    fun otherIdentity() { vault.save(JSONObject(session.toString()).put("userId", UUID.randomUUID().toString())) }
    fun restoreIdentity() { vault.save(session) }
    override fun close() { vault.save(session); server.close() }
}
