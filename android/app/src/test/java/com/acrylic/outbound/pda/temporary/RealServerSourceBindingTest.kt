package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.support.TestInfrastructure
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * L4 evidence for the LINK mapping install: the Android code itself reads the binding from the *real* server that the
 * resident IT (E2aServeBindingForAndroidIT) keeps alive on 127.0.0.1:19434, installs it into a real Room database, and
 * the local row must equal the server's linkage result. A repeated pass must stay idempotent.
 *
 * The transport has the production shape (OkHttp, the stack Retrofit uses; envelope `data`, Bearer session, recovery
 * epoch header). It is injected through SourceBindingRepository's internal constructor, which exists so the install
 * logic can be driven without simulating pairing/Keystore. Production's own PdaSession transport and the
 * ConnectionSettings cleartext seam are not exercised by this transport.
 *
 * Note: java.net.http is NOT usable here - android.jar has no such package, so an Android unit test cannot compile
 * against it (first attempt failed to compile; see the R06 debt list).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class RealServerSourceBindingTest {
    @Test fun androidInstallsTheBindingServedByTheRealServer() {
        val handshakePath = System.getenv("ANDROID_SOURCE_BINDING_HANDSHAKE")
        assumeTrue("Requires the resident server half (e2a Android LINK L4 orchestrator)", !handshakePath.isNullOrBlank())
        val handshake = JSONObject(File(handshakePath!!).readText())
        val baseUrl = handshake.getString("baseUrl")
        val scopeId = handshake.getString("scopeId")
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        val request: (String, String) -> JSONObject = { route, epoch ->
            val http = Request.Builder().url(baseUrl + route)
                .header("Authorization", "Bearer " + handshake.getString("token"))
                .header("X-Server-Recovery-Epoch", epoch)
                .get().build()
            client.newCall(http).execute().use { response ->
                val body = response.body?.string().orEmpty()
                check(response.isSuccessful) { "GET $route -> ${response.code} $body" }
                // The device consumes the envelope's data, exactly like a production session call does.
                JSONObject(body).getJSONObject("data")
            }
        }
        val current = {
            JSONObject()
                .put("userId", handshake.getString("userId"))
                .put("deviceId", handshake.getString("deviceId"))
                .put("warehouseId", handshake.getString("warehouseId"))
                .put("serverRecoveryEpoch", handshake.getString("serverRecoveryEpoch"))
                .put("localSessionId", "real-server-source-binding")
        }
        val db = TestInfrastructure.memoryDatabase()
        try {
            val repository = SourceBindingRepository(db, current, request)
            val installed = repository.installBindings()
            assertFalse("a single page must end the pass: ${installed.message}", installed.more)
            assertEquals(1, db.sourceBindings().count())
            val row = db.sourceBindings().get(scopeId)
            assertNotNull("the server's linkage result must land locally", row)
            assertEquals(handshake.getString("canonicalOrderId"), row!!.canonicalOrderId)
            assertEquals(handshake.getString("mappingVersion"), row.mappingVersion)
            assertEquals(handshake.getString("mappingOrigin"), row.mappingOrigin)
            assertEquals(handshake.getString("orderNo"), row.orderNo)
            assertEquals(handshake.getString("phase"), row.phase)
            // A second pass over the same page must not duplicate the row.
            repository.installBindings()
            assertEquals(1, db.sourceBindings().count())
        } finally {
            db.close()
        }
    }
}
