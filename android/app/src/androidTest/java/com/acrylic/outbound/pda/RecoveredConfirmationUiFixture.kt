package com.acrylic.outbound.pda

import android.security.keystore.KeyProtection
import android.security.keystore.KeyProperties
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.security.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Actual product GET/POST with an imported development receiving key, original LOGIN and a local loss proxy. */
internal class RecoveredConfirmationUiFixture(val app: PdaApplication) : AutoCloseable {
    val directory = File(app.filesDir, "recovered-confirmation-ui")
    private val input = JSONObject(File(directory, "private/input.json").readText())
    private val httpPort = InstrumentationRegistry.getArguments().getString("recoveredConfirmationHttpPort")
        ?.toIntOrNull()?.also { check(it in 1024..65535) } ?: 19434
    val caseId = input.getString("caseId")
    private val server = MockWebServer()
    private val vault = SessionVault(app)
    private val loseFirst = AtomicBoolean(true)
    val allowSuccess = AtomicBoolean(false)
    val calls = AtomicInteger()
    private val exchanges = AtomicInteger()
    private lateinit var session: JSONObject
    fun prepare() {
        check(BuildConfig.DEBUG && BuildConfig.RECOVERED_HANDOVER_DEVELOPMENT)
        check(InstrumentationRegistry.getArguments().getString("recoveredConfirmationUi") == "true")
        check(android.os.Build.HARDWARE == "ranchu")
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        importDevelopmentKey(); server.dispatcher = dispatcher(); server.start(19479)
        app.session.settings.bind(server.url("/api/v1/").toString(), input.getJSONObject("session").getString("deviceId"),
            DeviceKeys().publicKey(false))
        check(DeviceKeys().publicKey(false) == input.getString("devicePublicKey"))
        session = JSONObject(input.getJSONObject("session").toString())
            .put("timeAnchor", TrustedTime.anchor(Instant.now().toString(), TrustedTime.sample(app)))
        vault.save(session)
        readRecoveryKey()
        if (InstrumentationRegistry.getArguments().getString("recoveredConfirmationUiPhase") == "restart") {
            allowSuccess.set(true); loseFirst.set(false)
        }
    }
    private fun readRecoveryKey() {
        val started = TrustedTime.sample(app)
        val bytes = app.session.network().original("pda/signing-key", null, app.session.headers(), maximumBytes = 65536)
        val envelope = JSONObject(bytes.toString(Charsets.UTF_8))
        check(envelope.getString("code") == "OK" && envelope.getString("serviceMode") == "RECOVERY_PENDING")
        check(envelope.getString("serverRecoveryEpoch") == session.getString("serverRecoveryEpoch"))
        check(com.acrylic.outbound.pda.submission.SubmissionJson.canonical(envelope.getJSONObject("data")) ==
            com.acrylic.outbound.pda.submission.SubmissionJson.canonical(session.getJSONObject("signingKey")))
        com.acrylic.outbound.pda.sync.RecoveryProtection(app.database).observe(envelope)
        session.put("timeAnchor", TrustedTime.anchor(envelope.getString("serverTime"), started)); vault.save(session)
    }
    private fun importDevelopmentKey() {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias("outbound-device-v1")) return
        val key = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(File(directory, "private/device-private.der").readBytes()))
        val certificate = CertificateFactory.getInstance("X.509").generateCertificate(File(directory, "private/device-certificate.der").inputStream())
        val protection = KeyProtection.Builder(KeyProperties.PURPOSE_SIGN).setDigests(KeyProperties.DIGEST_SHA256).build()
        store.setEntry("outbound-device-v1", KeyStore.PrivateKeyEntry(key, arrayOf(certificate)), protection)
    }
    private fun dispatcher() = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val confirmation = request.path == "/api/v1/pda/handovers/$caseId/recovered-confirmation"
            if (confirmation && calls.get() > 0 && !allowSuccess.get()) return busy()
            val forwarded = forward(request)
            if (confirmation) {
                val ordinal = calls.incrementAndGet()
                File(directory, "command-$ordinal.json").writeBytes(request.body.clone().readByteArray())
                File(directory, "receipt-$ordinal.json").writeBytes(forwarded.second)
                if (loseFirst.compareAndSet(true, false)) return MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
            }
            return MockResponse().setResponseCode(forwarded.first).setHeader("Content-Type", "application/json")
                .setHeader("Cache-Control", "no-store").setBody(okio.Buffer().write(forwarded.second))
        }
    }
    private fun forward(request: RecordedRequest): Pair<Int, ByteArray> {
        check(request.path?.startsWith("/api/v1/pda/") == true)
        val ordinal = exchanges.incrementAndGet(); val started = android.os.SystemClock.elapsedRealtime()
        File(directory, "exchange-$ordinal.json").writeText(JSONObject().put("path", request.path)
            .put("method", request.method).put("startedElapsedMs", started).put("state", "STARTED").toString())
        val connection = URL("http://127.0.0.1:$httpPort${request.path}").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 30000; connection.readTimeout = 30000; connection.instanceFollowRedirects = false
            connection.requestMethod = checkNotNull(request.method)
            for (header in listOf("Authorization", "X-Server-Recovery-Epoch", "Content-Type", "Idempotency-Key"))
                request.getHeader(header)?.let { connection.setRequestProperty(header, it) }
            if (request.bodySize > 0) {
                connection.doOutput = true; connection.outputStream.use { it.write(request.body.clone().readByteArray()) }
            }
            val code = connection.responseCode
            val bytes = (if (code < 400) connection.inputStream else connection.errorStream).use { it.readBytes() }
            check(bytes.size in 1..32 * 1024 * 1024)
            File(directory, "exchange-$ordinal.json").writeText(JSONObject().put("path", request.path).put("method", request.method)
                .put("elapsedMs", android.os.SystemClock.elapsedRealtime() - started).put("status", code).put("bytes", bytes.size)
                .put("sha256", JwtVerifier.sha(bytes)).put("state", "COMPLETED").toString())
            return code to bytes
        } catch (error: Exception) {
            File(directory, "exchange-$ordinal.json").writeText(JSONObject().put("path", request.path).put("method", request.method)
                .put("elapsedMs", android.os.SystemClock.elapsedRealtime() - started).put("state", "FAILED")
                .put("errorType", error.javaClass.name).toString())
            throw error
        } finally { connection.disconnect() }
    }
    private fun busy(): MockResponse = MockResponse().setResponseCode(503).setHeader("Content-Type", "application/json")
        .setBody(JSONObject().put("code", "INTERNAL_ERROR").put("message", "isolated response recovery pending")
            .put("serviceMode", "RECOVERY_PENDING").put("serverRecoveryEpoch", session.getString("serverRecoveryEpoch"))
            .put("serverTime", Instant.now().toString()).toString())
    fun preserve() {
        check(app.database.sync().recovery()!!.mode == "RECONCILING")
        check(app.database.temporaryHandoverConfirmations().get(caseId) == null)
        check(app.database.temporaryHandoverActivations().get(caseId) == null)
        val stage = app.database.temporaryRecoveredHandoverStages().get(caseId)!!
        for ((kind, length) in listOf("MANIFEST" to stage.manifestLength, "PAYLOAD" to stage.payloadLength)) {
            val bytes = app.database.temporaryRecoveredHandoverChunks().read(caseId, kind, length)
            val file = File(directory, "original-$kind.json")
            if (file.exists()) check(file.readBytes().contentEquals(bytes)) else file.writeBytes(bytes)
        }
    }
    fun otherLogin() { vault.save(JSONObject(session.toString()).put("localSessionId", java.util.UUID.randomUUID().toString())) }
    fun restoreLogin() { vault.save(session) }
    override fun close() {
        vault.save(session); androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get(); server.close()
    }
}
