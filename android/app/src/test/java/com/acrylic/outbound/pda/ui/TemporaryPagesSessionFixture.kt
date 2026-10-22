package com.acrylic.outbound.pda.ui

import android.os.Looper
import android.provider.Settings
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.rescue.RescueBundle
import com.acrylic.outbound.pda.security.*
import com.acrylic.outbound.pda.temporary.TemporaryWorkFixture
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.robolectric.Shadows

/** Original signed TEMP input; synthetic JVM keys and loopback authority only. */
internal class TemporaryPagesSessionFixture : AutoCloseable {
    init { resetFactory() }
    val app = ApplicationProvider.getApplicationContext<RescueSignedFileFixture.TestApp>()
    private val provider = RescueFileJvmKeyStore.install()
    private val directory = File(app.cacheDir, "synthetic-temporary-pages-" + java.util.UUID.randomUUID()).apply { mkdirs() }
    val work = runBlocking(Dispatchers.IO) { TemporaryWorkFixture(directory) }
    val server = MockWebServer()
    val vault = SessionVault(app)
    val digest: String
    private var offline = false

    init {
        Settings.Global.putInt(app.contentResolver, Settings.Global.BOOT_COUNT, 1)
        server.start()
        val public = DeviceKeys().publicKey(false)
        digest = RescueBundle.sha(Base64.getDecoder().decode(public))
        app.session.settings.bind(server.url("/api/v1/").newBuilder().host("127.0.0.1").build().toString(),
            checkNotNull(work.fixture.auth).getString("deviceId"), public)
        vault.save(JSONObject(checkNotNull(work.fixture.auth).toString()).put("signingKey", work.fixture.key)
            .put("accessToken", "synthetic-temporary-pages-bearer").put("refreshToken", "synthetic-temporary-pages-refresh")
            .put("localSessionId", "synthetic-temporary-pages-session")
            .put("timeAnchor", TrustedTime.anchor(work.fixture.time.toString(), TrustedTime.sample(app))))
    }

    fun populate() = runBlocking<Unit>(Dispatchers.IO) {
        work.start()
        val voided = work.scan()
        work.processor.corrections.voidLast(work.id, voided.id, "合成未装车核对")
        work.scan(work.barcodes[1])
        work.receive("bad-input").also { work.processor.drain(work.id) }
        assertEquals(listOf("VOID", "ACTIVE"), work.db.temporaryScans().page(work.id, 0).map { it.status })
        assertEquals(3, work.db.temporaryScans().captures(work.id).size)
    }

    fun reply() {
        val envelope = JSONObject().put("code", "OK").put("message", "synthetic").put("data", JSONObject())
            .put("requestId", "synthetic-temporary-pages").put("serverTime", work.fixture.time.toString())
            .put("serverRecoveryEpoch", checkNotNull(work.fixture.auth).getString("serverRecoveryEpoch")).put("serviceMode", "NORMAL")
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(envelope.toString()))
    }

    fun request(): RecordedRequest = checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))
    fun stopServer() { server.shutdown(); offline = true }
    fun await(condition: () -> Boolean) {
        repeat(500) { Shadows.shadowOf(Looper.getMainLooper()).idle(); if (condition()) return; Thread.sleep(10) }
        fail("synthetic temporary pages did not settle")
    }
    override fun close() {
        try { if (!offline) server.shutdown(); vault.clear(); app.database.close(); work.close() }
        finally { RescueFileJvmKeyStore.remove(provider); resetFactory() }
    }
    private fun resetFactory() = org.robolectric.util.ReflectionHelpers.setStaticField(
        ViewModelProvider.AndroidViewModelFactory::class.java, "_instance", null)
}
