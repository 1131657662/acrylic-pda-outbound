package com.acrylic.outbound.pda

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.device.ScannerReceiver
import com.acrylic.outbound.pda.security.DeviceKeys
import com.acrylic.outbound.pda.temporary.*
import com.acrylic.outbound.pda.ui.*
import org.hamcrest.CoreMatchers.containsString
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Native Keystore and product pages over real HTTP, using synthetic isolated warehouse identities. */
class TemporaryExclusionDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val directory get() = File(app.filesDir, "p04-exclusion").apply { mkdirs() }
    private val input get() = JSONObject(File(directory, "input.json").readText())
    private val prepared get() = JSONObject(File(directory, "prepared.json").readText())

    private fun guard() {
        check(BuildConfig.DEBUG && app.packageName.matches(Regex("com\\.acrylic\\.outbound\\.pda\\.staging(\\.p04r[0-9]+)?")) &&
            android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("p04Exclusion") == "true")
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
    }

    @Test fun nativeSourceScansAndUploadsOriginalThroughProductPage() {
        guard(); check(!File(directory, "prepared.json").exists())
        val config = input; register(config)
        val actor = config.getJSONObject("operator")
        app.session.login(actor.getString("username"), actor.getString("password")); app.temporary.prepare { }
        val id = start(config); scan(id, config)
        submissionPage(id).use {
            await { onView(withId(R.id.freeze)).check(matches(isEnabled())) }
            onView(withId(R.id.freeze)).perform(scrollTo(), click())
            onView(withText("确认冻结")).perform(click())
            await { assertNotNull(app.database.temporarySubmissions().forOrder(id)) }
            await { onView(withId(R.id.upload)).check(matches(isDisplayed())).check(matches(isEnabled())) }
            onView(withId(R.id.upload)).perform(scrollTo(), click())
            await { onView(withId(R.id.status)).check(matches(withText(containsString("待独立确认")))) }
            shot("pending")
        }
        savePrepared(id)
    }

    @Test fun frozenNativeSourceResumesUploadThroughProductPage() {
        guard(); check(!File(directory, "prepared.json").exists())
        val submission = app.database.openHelper.readableDatabase.query("SELECT id FROM temporary_submission").use {
            assertEquals(1, it.count); check(it.moveToFirst()); it.getString(0)
        }
        val local = checkNotNull(app.database.temporarySubmissions().get(submission))
        assertEquals("FROZEN", local.state)
        assertEquals(2, app.database.temporaryScans().allScans(local.clientOrderId).size)
        val original = local.payload
        submissionPage(local.clientOrderId).use {
            await { onView(withId(R.id.upload)).check(matches(isDisplayed())).check(matches(isEnabled())) }
            onView(withId(R.id.upload)).perform(scrollTo(), click())
            await { onView(withId(R.id.status)).check(matches(withText(containsString("待独立确认")))) }
            shot("pending-resumed")
        }
        assertEquals(original, app.database.temporarySubmissions().forOrder(local.clientOrderId)?.payload)
        savePrepared(local.clientOrderId)
    }

    private fun savePrepared(id: String) {
        val local = checkNotNull(app.database.temporarySubmissions().forOrder(id))
        File(directory, "frozen-original.json").writeText(local.payload)
        File(directory, "prepared.json").writeText(JSONObject().put("clientOrderId", id).put("submissionId", local.id)
            .put("sha256", local.sha256).put("pendingReceipt", JSONObject(checkNotNull(local.receipt))).toString())
    }

    private fun register(config: JSONObject) {
        check(app.session.settings.device.isBlank()) { "Fresh staging identity required; never reset existing identity" }
        val actor = JSONObject(config.getJSONObject("operator").toString()).put("warehouseId", config.getString("warehouseId"))
        val web = FaultRestartWebClient(config.getString("baseUrl"), actor); web.login()
        val key = DeviceKeys().publicKey()
        val device = web.post("devices", JSONObject().put("deviceCode", UUID.randomUUID().toString())
            .put("name", "P04原机产品隔离模拟器").put("publicKey", key))
        app.session.settings.bind(config.getString("baseUrl"), device.getString("deviceId"), key)
        web.post("devices/${app.session.settings.device}/temporary-order", JSONObject().put("expectedVersion", "1")
            .put("enabled", true).put("reason", "P04合成隔离产品验证"))
        File(directory, "public-key.txt").writeText(key)
    }

    private fun start(config: JSONObject): String {
        val store = app.temporaryStore(); val drafts = TemporaryDraftRepository(store); val id = UUID.randomUUID().toString()
        val row = drafts.create(id)
        val saved = drafts.save(id, TemporaryPlanInput(row.revision, config.getString("vehicleNo"), "", "隔离P04原机验证",
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

    @Test fun supervisorSelectionThenProductFetchesTrustedMappingAndSuccess() {
        guard(); val state = prepared
        TemporaryExclusionWeb(input, directory).confirm(state.getString("submissionId"))
        assertTrue(app.database.sourceBindings().all().isEmpty())
        submissionPage(state.getString("clientOrderId")).use {
            await { onView(withId(R.id.refresh)).check(matches(isEnabled())) }
            onView(withId(R.id.refresh)).perform(scrollTo(), click())
            await { onView(withId(R.id.status)).check(matches(withText(containsString("成功原回执已核验")))) }
            shot("verified")
        }
        preserve("verified.json")
    }

    @Test fun freshProcessReadsVerifiedOriginalWithoutService() {
        guard(); val state = prepared
        submissionPage(state.getString("clientOrderId")).use {
            await { onView(withId(R.id.status)).check(matches(withText(containsString("成功原回执已核验")))) }
            onView(withId(R.id.upload)).check(matches(org.hamcrest.CoreMatchers.not(isEnabled())))
            shot("offline-reopened")
        }
        preserve("offline-reopened.json")
    }

    private fun preserve(name: String) {
        val state = prepared; val id = state.getString("submissionId")
        val local = app.temporarySubmissions.checked(id); val evidence = checkNotNull(app.database.temporarySuccess().evidence(id))
        assertEquals("SYNCED", local.state); assertFalse(local.autoRetry)
        assertEquals(state.getString("sha256"), local.sha256)
        assertEquals(File(directory, "frozen-original.json").readText(), local.payload)
        assertEquals(input.getString("targetId"), evidence.orderId)
        assertEquals(evidence.orderId, app.database.sourceBindings().all().single().canonicalOrderId)
        val scans = app.database.temporaryScans().allScans(state.getString("clientOrderId"))
        assertEquals(listOf(1L, 2L), scans.map { it.orderScanSeq })
        scans.forEach { assertNotNull(app.database.temporaryScans().guard(it.barcode)) }
        val original = checkNotNull(app.database.temporarySuccess().response(id, evidence.responseSha256)).responseBytes
        File(directory, "$name-original-response.json").writeBytes(original)
        File(directory, name).writeText(JSONObject().put("submissionId", id).put("state", local.state)
            .put("responseSha256", evidence.responseSha256).put("targetId", evidence.orderId)
            .put("originalPayloadUnchanged", true).put("localDeletionAllowed", false).toString())
    }

    private fun submissionPage(id: String) = ActivityScenario.launch<TemporarySubmissionActivity>(
        Intent(app, TemporarySubmissionActivity::class.java).putExtra("clientOrderId", id))

    private fun await(checker: () -> Unit) {
        var last: Throwable? = null
        repeat(150) { try { checker(); return } catch (error: Throwable) { last = error; Thread.sleep(200) } }
        throw AssertionError("P04 product state did not settle", last)
    }

    private fun shot(name: String) {
        onView(isRoot()).perform(object : androidx.test.espresso.ViewAction {
            override fun getConstraints() = isRoot()
            override fun getDescription() = "Preserve actual secure product rendering"
            override fun perform(controller: androidx.test.espresso.UiController, view: android.view.View) {
                val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        })
    }
}
