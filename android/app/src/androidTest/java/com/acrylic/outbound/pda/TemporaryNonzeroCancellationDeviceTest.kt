package com.acrylic.outbound.pda

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.device.ScannerReceiver
import com.acrylic.outbound.pda.evidence.E2aRequestSequence
import com.acrylic.outbound.pda.security.DeviceKeys
import com.acrylic.outbound.pda.temporary.*
import com.acrylic.outbound.pda.ui.*
import org.hamcrest.CoreMatchers.containsString
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Actual original Keystore and product pages. Isolated synthetic people, never a replacement source proof. */
class TemporaryNonzeroCancellationDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val directory get() = File(app.filesDir, "p05-cancel").apply { mkdirs() }
    private val input get() = JSONObject(File(directory, "input.json").readText())
    private val reason = "原机停止本车装载，保留原事实并请主管逐包卸货核实"
    private val evidenceLabel = "补传原机证据 · 保留待核对"

    private fun guard() {
        check(BuildConfig.DEBUG && app.packageName.matches(Regex("com\\.acrylic\\.outbound\\.pda\\.staging\\.p05r140[0-9]")))
        check(android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("p05Cancel") == "true")
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
    }

    @Test fun originalNonzeroBatchCancelsThenUploadsTrustedStopThroughProductPage() {
        guard(); check(!File(directory, "prepared.json").exists())
        val config = input; register(config)
        val actor = config.getJSONObject("operator")
        app.session.login(actor.getString("username"), actor.getString("password")); app.temporary.prepare { }
        val id = start(config); scan(id, config)
        page(id).use {
            await { onView(withId(R.id.freeze)).check(matches(isEnabled())) }
            onView(withId(R.id.freeze)).perform(scrollTo(), click()); onView(withText("确认冻结")).perform(click())
            await { onView(withId(R.id.upload)).check(matches(isDisplayed())).check(matches(isEnabled())) }
            val original = checkNotNull(app.database.temporarySubmissions().forOrder(id))
            File(directory, "frozen-original.json").writeText(original.payload)
            if (InstrumentationRegistry.getArguments().getString("uploaded") == "true") upload()
            cancel()
            assertEquals(original.payload, app.database.temporarySubmissions().get(original.id)?.payload)
            onView(withText(evidenceLabel)).perform(scrollTo(), click())
            await { assertEquals("PARTIAL", app.database.localE2aReceipts().queue(id)?.state) }
            await { onView(withText(evidenceLabel)).check(matches(isDisplayed())).check(matches(isEnabled())) }
            preserve(id, "prepared.json")
        }
    }

    @Test fun interruptedOriginalCancellationResumesItsExistingEvidenceOnly() {
        guard(); check(!File(directory, "prepared.json").exists())
        val frozen = JSONObject(File(directory, "frozen-original.json").readText())
        val id = frozen.getJSONObject("source").getString("clientOrderId")
        assertEquals("ABORTED", app.database.temporarySubmissions().forOrder(id)?.state)
        page(id).use {
            await { onView(withText(evidenceLabel)).check(matches(isDisplayed())).check(matches(isEnabled())) }
            onView(withText(evidenceLabel)).perform(scrollTo(), click())
            await { assertEquals("PARTIAL", app.database.localE2aReceipts().queue(id)?.state) }
            await { onView(withText(evidenceLabel)).check(matches(isEnabled())) }
            preserve(id, "prepared.json")
        }
    }

    private fun upload() {
        onView(withId(R.id.upload)).perform(scrollTo(), click())
        await { onView(withId(R.id.status)).check(matches(withText(containsString("待独立确认")))) }
    }

    private fun cancel() {
        onView(withText("取消原批次 · 保留实物占用")).perform(scrollTo(), click())
        onView(withId(R.id.abortReason)).perform(replaceText(reason), closeSoftKeyboard())
        onView(withId(R.id.abortConfirm)).perform(click())
        await { onView(withId(R.id.status)).check(matches(withText(containsString("取消回执已验签")))) }
    }

    private fun register(config: JSONObject) {
        check(app.session.settings.device.isBlank()) { "Fresh independent staging identity required" }
        val actor = JSONObject(config.getJSONObject("operator").toString()).put("warehouseId", config.getString("warehouseId"))
        val web = FaultRestartWebClient(config.getString("baseUrl"), actor); web.login()
        val key = DeviceKeys().publicKey()
        val device = web.post("devices", JSONObject().put("deviceCode", UUID.randomUUID().toString())
            .put("name", "P05原机主管取消隔离模拟器").put("publicKey", key))
        app.session.settings.bind(config.getString("baseUrl"), device.getString("deviceId"), key)
        web.post("devices/${app.session.settings.device}/temporary-order", JSONObject().put("expectedVersion", "1")
            .put("enabled", true).put("reason", "P05合成隔离产品验证"))
        File(directory, "public-key.txt").writeText(key)
    }

    private fun start(config: JSONObject): String {
        val drafts = TemporaryDraftRepository(app.temporaryStore()); val id = UUID.randomUUID().toString()
        val row = drafts.create(id)
        val saved = drafts.save(id, TemporaryPlanInput(row.revision, config.getString("vehicleNo"), "", "原机P05取消验证",
            listOf("003" to "1", "571" to "1")))
        drafts.start(id, saved.revision); return id
    }

    private fun scan(id: String, config: JSONObject) {
        ActivityScenario.launch<TemporaryWorkActivity>(Intent(app, TemporaryWorkActivity::class.java).putExtra("clientOrderId", id)).use {
            await { assertTrue(app.temporaryWork.state.value.ready) }
            val codes = config.getJSONArray("barcodes")
            repeat(codes.length()) { index ->
                app.sendBroadcast(Intent(ScannerReceiver.ACTION).putExtra(ScannerReceiver.BARCODE_KEY, codes.getString(index)))
                await { assertEquals((index + 1).toLong(), app.temporaryWork.state.value.total.count) }
            }
            await { assertEquals(0, app.temporaryWork.state.value.queued) }
        }
    }

    @Test fun restartedOriginalReadsCancellationAndRetriesOnlyOriginalEvidence() {
        guard(); val prior = JSONObject(File(directory, "prepared.json").readText())
        val id = prior.getString("clientOrderId")
        page(id).use {
            await { onView(withId(R.id.status)).check(matches(withText(containsString("取消回执已验签")))) }
            onView(withText(evidenceLabel)).perform(scrollTo(), click())
            await { onView(withText(evidenceLabel)).check(matches(isEnabled())) }
            preserve(id, "restarted.json")
        }
        val after = JSONObject(File(directory, "restarted.json").readText())
        assertEquals(prior.getString("receipt"), after.getString("receipt"))
        assertEquals(prior.getString("verifyOriginalKey"), after.getString("verifyOriginalKey"))
        assertEquals(prior.getJSONObject("receipts").toString(), after.getJSONObject("receipts").toString())
    }

    private fun preserve(id: String, name: String) {
        val local = checkNotNull(app.database.temporarySubmissions().forOrder(id))
        assertEquals("ABORTED", local.state); assertFalse(local.autoRetry); assertEquals(reason, local.abortReason)
        assertEquals(File(directory, "frozen-original.json").readText(), local.payload)
        val scans = app.database.temporaryScans().allScans(id)
        assertEquals(listOf(1L, 2L), scans.map { it.orderScanSeq })
        scans.forEach { assertNotNull(app.database.temporaryScans().guard(it.barcode)) }
        val plan = checkNotNull(app.database.localE2aTransfers().get(id))
        val receipts = JSONObject()
        E2aRequestSequence(app.database, plan).all().forEach { request ->
            val saved = checkNotNull(app.database.localE2aReceipts().get(request.originalKey))
            assertEquals("VERIFIED", saved.state)
            val bytes = checkNotNull(saved.responseBytes)
            File(directory, "${request.operation}-${request.originalKey}-response.json").writeBytes(bytes)
            File(directory, "${request.operation}-${request.originalKey}-request.json").writeText(request.requestJson)
            receipts.put(request.originalKey, android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
        }
        val verified = JSONObject(checkNotNull(app.database.localE2aReceipts().get(plan.verifyOriginalKey)?.responseBytes)
            .toString(Charsets.UTF_8))
        assertEquals("VERIFIED_COMPLETE_AT_STOP", verified.getString("status"))
        File(directory, name).writeText(JSONObject().put("clientOrderId", id).put("submissionId", local.id)
            .put("state", local.state).put("sha256", local.sha256).put("receipt", local.receipt)
            .put("verifyOriginalKey", plan.verifyOriginalKey).put("verification", verified).put("receipts", receipts)
            .put("originalPayloadUnchanged", true).put("localDeletionAllowed", false).toString())
    }

    private fun page(id: String) = ActivityScenario.launch<TemporarySubmissionActivity>(
        Intent(app, TemporarySubmissionActivity::class.java).putExtra("clientOrderId", id))

    private fun await(checker: () -> Unit) {
        var last: Throwable? = null
        repeat(150) { try { checker(); return } catch (error: Throwable) { last = error; Thread.sleep(200) } }
        throw AssertionError("P05 product state did not settle", last)
    }
}
