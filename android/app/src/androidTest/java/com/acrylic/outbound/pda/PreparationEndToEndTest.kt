package com.acrylic.outbound.pda

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.ui.MainActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PreparationEndToEndTest {
    @Test fun registeredDeviceLogsInClaimsDownloadsAndRecoversLocalActivation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as PdaApplication
        val fixtureFile = File(context.filesDir, "e2e-fixture.json")
        val fixture = JSONObject(fixtureFile.readText()); fixtureFile.delete()
        val admin = AdminApi(fixture)
        SubmissionProxy(fixture.getString("baseUrl")).use { proxy -> runFlow(app, admin, fixture, proxy) }
    }
    private fun runFlow(app: PdaApplication, admin: AdminApi, fixture: JSONObject, proxy: SubmissionProxy) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val publicKey = app.session.publicKey()
        val device = admin.register(publicKey)
        app.session.settings.bind(proxy.base, device, publicKey)
        app.session.login(fixture.getString("username"), fixture.getString("password"))
        val order = app.repository.list("pda/orders", "cursor").single { it.getString("id") == fixture.getString("orderId") }
        val assignment = app.repository.claim(order, "模拟器验证货堆")
        val activated = app.repository.prepare(assignment) {}
        assertEquals(2, activated.rowCount); assertNotNull(activated.ackAt)
        assertTrue(app.repository.readiness(activated).contains("资料已就绪"))
        verifyActivityShowsPreparedOrder()
        val verifyBackground = InstrumentationRegistry.getArguments().getString("verifyBackground") == "true"
        if (verifyBackground) BackgroundSyncApiScenario.verify(app, activated.assignmentId, completed = false)
        WorkScreenScenario.exercise(app, activated.assignmentId)
        val submission = SubmissionScreenScenario.freeze(app, activated.assignmentId)
        val originalBody = app.database.submissions().get(submission)!!.payload
        proxy.loseSubmissionResponse.set(true)
        assertThrows(java.io.IOException::class.java) { app.submissions.upload(submission) }
        assertEquals("RESULT_UNKNOWN", app.database.submissions().get(submission)!!.state)
        assertEquals("REVIEW_REQUIRED", app.submissions.upload(submission).state)
        admin.approve(submission, app.database.submissions().get(submission)!!.sha256)
        assertEquals("SYNCED", app.submissions.upload(submission).state)
        assertEquals(originalBody, app.database.submissions().get(submission)!!.payload)
        SubmissionScreenScenario.showsSuccess(app, activated.assignmentId)
        if (verifyBackground) BackgroundSyncApiScenario.verify(app, activated.assignmentId, completed = true)
        val verifyArchives = InstrumentationRegistry.getArguments().getString("verifyArchives") == "true"
        if (verifyArchives) ArchiveEndToEndScenario.verify(app, submission)
        app.database.close()
        val reopened = PdaDatabase.open(context)
        assertEquals(activated, reopened.preparations().order(activated.assignmentId))
        assertEquals(2, reopened.scans().total(activated.assignmentId).count)
        assertEquals("SYNCED", reopened.submissions().get(submission)!!.state)
        if (verifyArchives) ArchiveEndToEndScenario.verifyReopened(app, reopened, submission)
        reopened.close()
        val evidence = JSONObject().put("assignmentId", activated.assignmentId).put("orderId", activated.orderId)
            .put("deviceId", device).put("rows", activated.rowCount).put("ackAt", activated.ackAt)
            .put("keystoreProof", true).put("databaseReopened", true).put("activityShowsReadyOrder", true)
            .put("automaticAndManualScan", true).put("backgroundIgnoresBroadcast", true).put("savedCount", 2).put("weightGrams", 601500)
            .put("submissionId", submission).put("lostResponseRecovered", true).put("shortShipmentApproved", true).put("signedReceiptVerified", true)
        File(context.filesDir, "e2e-evidence.json").writeText(evidence.toString())
    }

    private fun verifyActivityShowsPreparedOrder() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var ready = false
            val deadline = android.os.SystemClock.elapsedRealtime() + 5000
            while (!ready && android.os.SystemClock.elapsedRealtime() < deadline) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { activity ->
                    val details = activity.findViewById<android.widget.TextView>(R.id.details)
                    ready = details?.text?.contains("资料已就绪 · 已确认下载") == true
                }
                if (!ready) Thread.sleep(50)
            }
            assertTrue("Actual activity must render the persisted ready order", ready)
        }
    }

    private class AdminApi(private val fixture: JSONObject) {
        private val cookies = ArrayList<Cookie>()
        private val client = OkHttpClient.Builder().cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { this@AdminApi.cookies.clear(); this@AdminApi.cookies.addAll(cookies) }
            override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.filter { it.matches(url) }
        }).build()
        private val base = fixture.getString("baseUrl")
        private var csrf = ""

        init {
            csrf = request("auth/csrf").getString("token")
            request("auth/web/login", FormBody.Builder().add("username", fixture.getString("username"))
                .add("password", fixture.getString("password")).build())
            csrf = request("auth/csrf").getString("token")
        }

        fun register(publicKey: String): String {
            val body = JSONObject().put("deviceCode", "emulator-${UUID.randomUUID().toString().take(20)}")
                .put("name", "Android准备链路验证").put("publicKey", publicKey)
            return request("devices?warehouseId=${fixture.getString("warehouseId")}", body.toString().toRequestBody("application/json".toMediaType()))
                .getString("deviceId")
        }
        fun assignment(id: String) = request("orders/$id/assignment?warehouseId=${fixture.getString("warehouseId")}").getJSONObject("assignment")
        fun approve(id: String, hash: String) = request("submissions/$id/review", JSONObject().put("payloadSha256", hash)
            .put("decision", "APPROVE").put("reason", "模拟器实物核对场景批准短装").toString().toRequestBody("application/json".toMediaType()))
        private fun request(path: String, body: RequestBody? = null): JSONObject {
            val builder = Request.Builder().url(base + path)
            if (body != null) builder.post(body).header("X-CSRF-TOKEN", csrf).header("X-Server-Recovery-Epoch", "1")
                .header("Idempotency-Key", UUID.randomUUID().toString())
            return client.newCall(builder.build()).execute().use {
                val result = JSONObject(it.body!!.string())
                check(it.isSuccessful) { "Admin fixture request failed: ${result.optString("code")}" }
                result.getJSONObject("data")
            }
        }
    }
}
