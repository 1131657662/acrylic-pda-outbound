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

/** Real temporary preparation HTTP and product UI; synthetic isolated identity and stock. */
class TemporaryScanAdjustmentDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val directory get() = File(app.filesDir, "scan-adjustment").apply { mkdirs() }

    private fun prepare(): Pair<String, List<String>> {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("scanAdjustment") == "true")
        val input = JSONObject(File(app.filesDir, "fault-restart/private/input.json").readText()).getJSONObject("android")
        val base = "http://127.0.0.1:19434/api/v1/"
        val web = FaultRestartWebClient(base, input); web.login()
        register(web, base)
        val barcodes = TemporaryAdjustmentStock.publish(web, input)
        app.session.login(input.getString("username"), input.getString("password"))
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        app.temporary.prepare { }
        val store = app.temporaryStore(); val drafts = TemporaryDraftRepository(store); val id = UUID.randomUUID().toString()
        val initial = drafts.create(id)
        val saved = drafts.save(id, TemporaryPlanInput(initial.revision, "隔离TEMP调整车", "", "合成页面验证",
            barcodes.map { it.take(3) to "1" }))
        drafts.start(id, saved.revision)
        return id to barcodes
    }
    private fun register(web: FaultRestartWebClient, base: String) {
        check(app.session.settings.device.isBlank()) { "This consumer requires a fresh isolated device; previous facts must be preserved" }
        val publicKey = DeviceKeys().publicKey()
        val device = web.post("devices", JSONObject().put("deviceCode", UUID.randomUUID().toString())
            .put("name", "TEMP已扫码包调整隔离模拟器").put("publicKey", publicKey))
        app.session.settings.bind(base, device.getString("deviceId"), publicKey)
        web.post("devices/${app.session.settings.device}/temporary-order", JSONObject().put("expectedVersion", "1")
            .put("enabled", true).put("reason", "隔离TEMP页面验证准入"))
    }
    @Test fun locateOlderTemporaryPackageThenRescanRetainsHistoryAndFrozenBody() {
        val (id, codes) = prepare()
        ActivityScenario.launch<TemporaryWorkActivity>(Intent(app, TemporaryWorkActivity::class.java).putExtra("clientOrderId", id)).use {
            await { assertTrue(app.temporaryWork.state.value.ready && app.temporaryWork.state.value.queued == 0) }
            codes.forEachIndexed { index, code -> broadcast(code); await { assertEquals((index + 1).toLong(), app.temporaryWork.state.value.total.count) } }
            onView(withId(R.id.adjustPackages)).perform(scrollTo(), click())
            locate(codes.first())
            assertEquals(2, app.database.temporaryScans().captures(id).size)
            confirm(codes.first())
            await { onView(withId(R.id.status)).check(matches(withText(containsString("调整已保存到本机")))) }
            assertEquals(listOf("VOID", "ACTIVE"), app.database.temporaryScans().allScans(id).map { row -> row.status })
            shot("temporary-voided")
            onView(withId(R.id.back)).perform(scrollTo(), click())
            await { assertTrue(app.temporaryWork.state.value.ready && app.temporaryWork.state.value.total.count == 1L) }
            broadcast(codes.first()); await { assertTrue(app.temporaryWork.state.value.total.count == 2L && app.temporaryWork.state.value.queued == 0) }
        }
        val frozen = TemporaryFreeze(app.temporaryStore()).freeze(id, "")
        assertEquals(listOf(1L, 2L, 3L), app.database.temporaryScans().allScans(id).map { it.orderScanSeq })
        rejectFrozen(id, codes.first(), frozen.payload)
        File(directory, "temporary-result.json").writeText(JSONObject().put("clientOrderId", id).put("state", frozen.state)
            .put("sha256", frozen.sha256).put("locatorAddedCapture", false).put("originalSequenceRetained", true)
            .put("localDeletionAllowed", false).toString())
    }
    @Test fun savedFrozenTemporaryPageKeepsAdjustmentDisabledAcrossNewProcess() {
        check(BuildConfig.DEBUG && InstrumentationRegistry.getArguments().getString("scanAdjustment") == "true")
        val saved = JSONObject(File(directory, "temporary-result.json").readText()); val id = saved.getString("clientOrderId")
        val before = checkNotNull(app.database.temporarySubmissions().forOrder(id))
        val code = app.database.temporaryScans().allScans(id).last().barcode
        rejectFrozen(id, code, before.payload)
        ActivityScenario.launch<TemporaryDetailsActivity>(Intent(app, TemporaryDetailsActivity::class.java).putExtra("clientOrderId", id)).use {
            await { onView(withId(R.id.create)).check(matches(withText(containsString("冻结或停止")))) }
            onView(withId(R.id.create)).check(matches(org.hamcrest.CoreMatchers.not(isEnabled())))
        }
        assertEquals(before, app.database.temporarySubmissions().forOrder(id))
        File(directory, "temporary-readonly-completed.txt").writeText("New process: frozen locator and details both disable adjustment; immutable temporary submission retained.")
    }
    private fun rejectFrozen(id: String, code: String, payload: String) {
        ActivityScenario.launch<ScanAdjustmentActivity>(Intent(app, ScanAdjustmentActivity::class.java).putExtra("clientOrderId", id)).use {
            locate(code)
            onView(org.hamcrest.CoreMatchers.allOf(isDescendantOfA(withId(R.id.rows)), withText(containsString(code)))).check(matches(org.hamcrest.CoreMatchers.not(isEnabled())))
            await { onView(withId(R.id.status)).check(matches(withText(containsString("只可核对原记录")))) }
            assertEquals(payload, app.database.temporarySubmissions().forOrder(id)!!.payload)
            assertEquals(1, app.database.temporaryScans().events(id).count { row -> row.type == "VOID" })
            shot("temporary-frozen-rejected")
        }
    }
    private fun locate(code: String) {
        await { onView(withId(R.id.locate)).check(matches(isEnabled())) }
        onView(withId(R.id.barcode)).perform(scrollTo(), replaceText(code), closeSoftKeyboard())
        onView(withId(R.id.locate)).perform(scrollTo(), click())
        await { onView(withId(R.id.status)).check(matches(withText(containsString("已定位原记录")))) }
    }
    private fun confirm(code: String) {
        onView(org.hamcrest.CoreMatchers.allOf(isDescendantOfA(withId(R.id.rows)), withText(containsString(code)))).perform(scrollTo(), click())
        onView(withHint("实物核对及调整原因")).perform(replaceText("隔离合成实物核对：未装车，保留原记录"), closeSoftKeyboard())
        onView(withText("确认调整")).perform(click())
    }
    private fun broadcast(code: String) = app.sendBroadcast(Intent(ScannerReceiver.ACTION).putExtra(ScannerReceiver.BARCODE_KEY, code))
    private fun await(check: () -> Unit) {
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30); var last: Throwable? = null
        do { try { check(); return } catch (error: Throwable) { last = error }; Thread.sleep(100) } while (System.nanoTime() < deadline)
        throw AssertionError("Temporary product page did not reach expected state", last)
    }
    private fun shot(name: String) {
        onView(isRoot()).perform(object : androidx.test.espresso.ViewAction {
            override fun getConstraints() = isRoot()
            override fun getDescription() = "Record actual temporary adjustment layout"
            override fun perform(controller: androidx.test.espresso.UiController, view: android.view.View) {
                val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        })
    }
}
