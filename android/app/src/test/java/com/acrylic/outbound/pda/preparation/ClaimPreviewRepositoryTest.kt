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
    @Test fun previewOnlyGetsAndConfirmationUsesOriginalVersionAndSinglePost() = scenario { repository, server, db, _ ->
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

    @Test fun changedVersionOrForeignWarehouseCannotBecomeCheckedPreview() = scenario { repository, server, db, _ ->
        for (changed in listOf(preview().put("rowVersion", "3"), preview().put("warehouseId", "foreign"))) {
            server.enqueue(response(changed))
            assertThrows(Exception::class.java) { repository.preview(preview()) }
            assertEquals("GET", server.takeRequest().method)
            assertNull(db.preparations().intent("order", "user", "device"))
        }
        assertEquals(2, server.requestCount)
    }

    @Test fun claimRecheckRejectsMissingOrForeignWarehouse() {
        ClaimPreviewWarehouseGuard.requireMatches(preview(), "order", "warehouse")
        val changedPreviews = listOf(preview().apply { remove("warehouseId") }, preview().put("warehouseId", "foreign"))
        for (changed in changedPreviews) {
            assertThrows(Exception::class.java) {
                ClaimPreviewWarehouseGuard.requireMatches(changed, "order", "warehouse")
            }
        }
    }

    @Test fun changedSessionDuringClaimPreviewCannotPersistIntent() = scenario { repository, server, db, vault ->
        for (field in listOf("userId", "deviceId", "warehouseId", "localSessionId", "serverRecoveryEpoch")) {
            val original = repository.session.requireSession()
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                    vault.save(JSONObject(original.toString()).put(field, "2"))
                    return response(preview(), if (field == "serverRecoveryEpoch") "2" else "1")
                }
            }
            assertThrows(SessionChanged::class.java) { repository.claim(preview(), "合成区域") }
            assertEquals("GET", server.takeRequest().method)
            assertNull(db.preparations().intent("order", "user", "device"))
            vault.save(original)
        }
        assertEquals(5, server.requestCount)
    }

    @Test fun changedSessionDuringRetryRefreshPreservesIntentWithoutPost() = scenario { repository, server, db, vault ->
        val intent = com.acrylic.outbound.pda.data.ClaimIntent("order", "user", "device", "original-key",
            JSONObject().put("expectedVersion", "2").toString(), "1", lastError = "preserve-original")
        db.preparations().insertIntent(intent)
        for (field in listOf("userId", "deviceId", "warehouseId", "localSessionId", "serverRecoveryEpoch")) {
            val original = repository.session.requireSession()
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                    vault.save(JSONObject(original.toString()).put(field, "2"))
                    return response(JSONObject(), if (field == "serverRecoveryEpoch") "2" else "1")
                }
            }
            assertThrows(SessionChanged::class.java) { repository.retryClaim(intent) }
            assertEquals("/api/v1/pda/devices/me", server.takeRequest().path)
            assertEquals(intent, db.preparations().intent("order", "user", "device"))
            vault.save(original)
        }
        assertEquals(5, server.requestCount)
    }

    @Test fun changedSessionBeforeRejectedReplacementPreservesIntent() = scenario { repository, server, db, vault ->
        val intent = com.acrylic.outbound.pda.data.ClaimIntent("order", "user", "device", "rejected-key",
            JSONObject().put("expectedVersion", "1").toString(), "1", "REJECTED", "preserve-rejection")
        db.preparations().insertIntent(intent)
        for (field in listOf("userId", "deviceId", "warehouseId", "localSessionId", "serverRecoveryEpoch")) {
            val original = repository.session.requireSession()
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                    vault.save(JSONObject(original.toString()).put(field, "2"))
                    return response(preview(), if (field == "serverRecoveryEpoch") "2" else "1")
                }
            }
            assertThrows(SessionChanged::class.java) { repository.claim(preview(), "合成区域") }
            assertEquals("GET", server.takeRequest().method)
            assertEquals(intent, db.preparations().intent("order", "user", "device"))
            assertTrue(db.preparations().history("order", "user", "device").isEmpty())
            vault.save(original)
        }
        assertEquals(5, server.requestCount)
    }

    @Test fun lostResponsePreservesIntentAndRetryUsesOriginalKeyAndBody() = scenario { repository, server, db, _ ->
        val intent = com.acrylic.outbound.pda.data.ClaimIntent("order", "user", "device", "original-key",
            JSONObject().put("expectedVersion", "2").put("areaNote", "合成区域").toString(), "1")
        db.preparations().insertIntent(intent)
        server.enqueue(response(JSONObject()))
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertThrows(PdaTransportFailure::class.java) { repository.retryClaim(intent) }
        assertEquals(intent, db.preparations().intent("order", "user", "device"))
        server.enqueue(response(JSONObject())); server.enqueue(response(JSONObject().put("id", "assignment")))
        assertEquals("assignment", repository.retryClaim(intent).getString("id"))
        repeat(2) {
            assertEquals("/api/v1/pda/devices/me", server.takeRequest().path)
            val post = server.takeRequest()
            assertEquals("POST", post.method); assertEquals("/api/v1/pda/orders/order/assignments", post.path)
            assertEquals(intent.key, post.getHeader("Idempotency-Key"))
            assertEquals(intent.recoveryEpoch, post.getHeader("X-Server-Recovery-Epoch"))
            assertEquals(intent.body, post.body.readUtf8())
        }
        assertEquals(4, server.requestCount)
        assertNull(db.preparations().intent("order", "user", "device"))
    }

    @Test fun conflictKeepsRejectedOriginalIntentWithoutAnotherPost() = scenario { repository, server, db, _ ->
        val intent = com.acrylic.outbound.pda.data.ClaimIntent("order", "user", "device", "original-key",
            JSONObject().put("expectedVersion", "2").toString(), "1")
        db.preparations().insertIntent(intent)
        server.enqueue(response(JSONObject()))
        server.enqueue(response(JSONObject(), code = "ORDER_VERSION_CONFLICT").setResponseCode(409))
        val failure = assertThrows(ApiFailure::class.java) { repository.retryClaim(intent) }
        val rejected = db.preparations().intent("order", "user", "device")!!
        assertEquals(intent.copy(status = "REJECTED", lastError = failure.message), rejected)
        assertThrows(IllegalStateException::class.java) { repository.retryClaim(rejected) }
        assertEquals(2, server.requestCount)
    }

    private fun scenario(test: (PreparationRepository, MockWebServer, PdaDatabase, SessionVault) -> Unit) = runBlocking<Unit>(Dispatchers.IO) {
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
                test(PreparationRepository(context, db, session), server, db, vault)
            }
        } finally { vault.clear(); db.close(); context.deleteDatabase("outbound-pda.db"); Security.removeProvider(provider.name) }
    }

    private fun preview() = JSONObject().put("id", "order").put("orderNo", "SYNTH-ORDER")
        .put("vehicleNo", "合成车").put("status", "READY").put("rowVersion", "2").put("warehouseId", "warehouse")
    private fun response(data: JSONObject, epoch: String = "1", code: String = "OK") = MockResponse().setHeader("Content-Type", "application/json")
        .setBody(JSONObject().put("code", code).put("message", "合成响应").put("data", data).put("requestId", "synthetic")
            .put("serverTime", "2026-10-20T00:00:00Z").put("serverRecoveryEpoch", epoch).put("serviceMode", "NORMAL").toString())
}
