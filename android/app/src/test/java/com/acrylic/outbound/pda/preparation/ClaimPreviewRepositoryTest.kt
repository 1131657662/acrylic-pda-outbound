package com.acrylic.outbound.pda.preparation

import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.network.*
import com.acrylic.outbound.pda.security.*
import com.acrylic.outbound.pda.retention.JvmSessionKeyStore
import com.acrylic.outbound.pda.sync.RecoveryProtection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.security.Security

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ClaimPreviewRepositoryTest {
    @Test fun previewOnlyGetsAndConfirmationUsesOriginalVersionAndSinglePost() = scenario { repository, server, db ->
        val original = preview()
        server.enqueue(response(original))
        val checked = repository.preview(original)
        assertEquals("GET", server.takeRequest().method)
        assertNull(db.preparations().intent("order", "user", "device"))
        assertEquals(1, server.requestCount)
        server.enqueue(response(original)); server.enqueue(response(JSONObject()))
        server.enqueue(response(JSONObject().put("id", "assignment")))
        assertEquals("assignment", repository.claim(checked, "合成区域").getString("id"))
        assertEquals("GET", server.takeRequest().method)
        assertEquals("/api/v1/pda/devices/me", server.takeRequest().path)
        val claim = server.takeRequest()
        assertEquals("POST", claim.method); assertEquals("/api/v1/pda/orders/order/assignments", claim.path)
        assertEquals("2", JSONObject(claim.body.readUtf8()).getString("expectedVersion"))
        assertFalse(claim.getHeader("Idempotency-Key").isNullOrBlank())
        assertEquals(4, server.requestCount)
        assertNull(db.preparations().intent("order", "user", "device"))
    }

    @Test fun changedVersionOrForeignWarehouseCannotBecomeCheckedPreview() = scenario { repository, server, db ->
        for (changed in listOf(preview().put("rowVersion", "3"), preview().put("warehouseId", "foreign"))) {
            server.enqueue(response(changed))
            assertThrows(Exception::class.java) { repository.preview(preview()) }
            assertEquals("GET", server.takeRequest().method)
            assertNull(db.preparations().intent("order", "user", "device"))
        }
        assertEquals(2, server.requestCount)
    }

    private fun scenario(test: (PreparationRepository, MockWebServer, PdaDatabase) -> Unit) = runBlocking<Unit>(Dispatchers.IO) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val provider = JvmSessionKeyStore.install(); context.deleteDatabase("outbound-pda.db")
        val db = PdaDatabase.open(context); val vault = SessionVault(context)
        try {
            MockWebServer().use { server ->
                server.start()
                vault.save(JSONObject().put("userId", "user").put("deviceId", "device").put("warehouseId", "warehouse")
                    .put("accessToken", "synthetic-access").put("serverRecoveryEpoch", "1").put("localSessionId", "synthetic"))
                context.getSharedPreferences("connection", 0).edit().putString("server", server.url("/api/v1/").toString())
                    .putString("device", "device").commit()
                val session = PdaSession(context, ConnectionSettings(context), DeviceKeys(), vault, RecoveryProtection(db))
                test(PreparationRepository(context, db, session), server, db)
            }
        } finally { vault.clear(); db.close(); context.deleteDatabase("outbound-pda.db"); Security.removeProvider(provider.name) }
    }

    private fun preview() = JSONObject().put("id", "order").put("orderNo", "SYNTH-ORDER")
        .put("vehicleNo", "合成车").put("status", "READY").put("rowVersion", "2").put("warehouseId", "warehouse")
    private fun response(data: JSONObject) = MockResponse().setHeader("Content-Type", "application/json")
        .setBody(JSONObject().put("code", "OK").put("message", "成功").put("data", data).put("requestId", "synthetic")
            .put("serverTime", "2026-10-20T00:00:00Z").put("serverRecoveryEpoch", "1").put("serviceMode", "NORMAL").toString())
}
