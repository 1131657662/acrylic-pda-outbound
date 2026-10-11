package com.acrylic.outbound.pda

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.device.ScannerReceiver
import com.acrylic.outbound.pda.security.DeviceKeys
import com.acrylic.outbound.pda.ui.*
import org.hamcrest.CoreMatchers.containsString
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Real emulator product UI/Keystore/HTTP; synthetic isolated orders, no physical-device claim. */
class ScanAdjustmentDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val directory get() = File(app.filesDir, "scan-adjustment").apply { mkdirs() }
    private val base = "http://127.0.0.1:19434/api/v1/"

    private fun prepare(): Pair<String, List<String>> {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("scanAdjustment") == "true")
        val f = JSONObject(File(app.filesDir, "fault-restart/private/input.json").readText()).getJSONObject("android")
        val web = FaultRestartWebClient(base, f); web.login(); web.withdrawAndPublish()
        val publicKey = DeviceKeys().publicKey()
        val device = web.post("devices", JSONObject().put("deviceCode", UUID.randomUUID().toString())
            .put("name", "已扫码包调整隔离模拟器").put("publicKey", publicKey))
        app.session.settings.bind(base, device.getString("deviceId"), publicKey)
        app.session.login(f.getString("username"), f.getString("password"))
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        val assignment = app.repository.claim(web.detail().getJSONObject("order"), "隔离已扫码包调整")
        val prepared = app.repository.prepare(assignment) { }; app.sync.refreshOrders()
        val scans = f.getJSONObject("oldBody").getJSONArray("scans")
        return prepared.assignmentId to (0 until scans.length()).map { scans.getJSONObject(it).getString("barcode") }
    }
    @Test fun scannerLocatesOldPackageWithoutCaptureThenAdjustmentRescanAndSuccessRetainHistory() {
        val (assignment, barcodes) = prepare()
        scanAdjustRescan(assignment, barcodes)
        val frozen = app.freezer.freeze(assignment, "")
        assertEquals(listOf(1L, 2L, 3L), app.database.scans().page(assignment, 0, 20).map { it.orderScanSeq })
        val success = app.submissions.upload(frozen.id); assertEquals("SYNCED", success.state)
        frozenAdjustmentRejected(assignment, barcodes.last(), frozen.payload)
        File(directory, "result.json").writeText(JSONObject().put("assignmentId", assignment).put("submissionId", frozen.id)
            .put("sha256", success.sha256).put("state", success.state).put("locatorAddedCapture", false)
            .put("originalSequenceRetained", true).put("localDeletionAllowed", false).toString())
    }
    @Test fun savedFrozenFormalPageKeepsAdjustmentDisabledAcrossNewProcess() {
        check(BuildConfig.DEBUG && InstrumentationRegistry.getArguments().getString("scanAdjustment") == "true")
        val saved = JSONObject(File(directory, "result.json").readText())
        val assignment = saved.getString("assignmentId")
        val before = checkNotNull(app.database.submissions().forOrder(assignment))
        val barcode = app.database.scans().page(assignment, 0, 20).last().barcode
        frozenAdjustmentRejected(assignment, barcode, before.payload)
        ActivityScenario.launch<WorkDetailsActivity>(Intent(app, WorkDetailsActivity::class.java).putExtra("assignmentId", assignment)).use {
            awaitView { onView(withId(R.id.adjustPackages)).check(matches(withText(containsString("冻结或停止")))) }
            onView(withId(R.id.adjustPackages)).check(matches(org.hamcrest.CoreMatchers.not(isEnabled())))
        }
        assertEquals(before, app.database.submissions().forOrder(assignment))
        File(directory, "formal-readonly-completed.txt").writeText("New process: frozen locator and details both disable adjustment; immutable submission retained.")
    }
    private fun scanAdjustRescan(assignment: String, barcodes: List<String>) {
        ActivityScenario.launch<WorkActivity>(Intent(app, WorkActivity::class.java).putExtra("assignmentId", assignment)).use {
            awaitState { app.work.state.value.ready && app.work.state.value.queued == 0 }
            barcodes.forEachIndexed { index, barcode ->
                broadcast(barcode); awaitState { app.work.state.value.order?.actualCount == (index + 1).toLong() }
            }
            val originals = app.database.scans().page(assignment, 0, 20)
            onView(withId(R.id.adjustPackages)).perform(scrollTo(), click())
            awaitView { onView(withId(R.id.status)).check(matches(withText(containsString("每页最多20条")))) }
            broadcast(barcodes.first())
            awaitView { onView(withId(R.id.status)).check(matches(withText(containsString("已定位原记录")))) }
            assertEquals(2L, app.database.scans().total(assignment).count)
            assertEquals(2, app.database.captures().page(assignment, 0, 20).size)
            selectOriginal(barcodes.first()); confirmReason()
            awaitView { onView(withId(R.id.status)).check(matches(withText(containsString("调整已保存到本机")))) }
            assertEquals("VOID", app.database.scans().get(originals.first().captureId)!!.status)
            assertEquals("ACTIVE", app.database.scans().get(originals.last().captureId)!!.status)
            assertEquals(1L, app.database.scans().total(assignment).count)
            shot("voided")
            onView(withId(R.id.back)).perform(scrollTo(), click())
            awaitState { app.work.state.value.ready && app.work.state.value.order?.actualCount == 1L }
            broadcast(barcodes.first()); awaitState { app.work.state.value.order?.actualCount == 2L && app.work.state.value.queued == 0 }
        }
    }
    private fun frozenAdjustmentRejected(assignment: String, barcode: String, payload: String) {
        ActivityScenario.launch<ScanAdjustmentActivity>(Intent(app, ScanAdjustmentActivity::class.java).putExtra("assignmentId", assignment)).use {
            awaitView { onView(withId(R.id.locate)).check(matches(isEnabled())) }
            broadcast(barcode); awaitView { onView(withId(R.id.status)).check(matches(withText(containsString("已定位原记录")))) }
            onView(org.hamcrest.CoreMatchers.allOf(isDescendantOfA(withId(R.id.rows)), withText(containsString(barcode)))).check(matches(org.hamcrest.CoreMatchers.not(isEnabled())))
            awaitView { onView(withId(R.id.status)).check(matches(withText(containsString("只可核对原记录")))) }
            assertEquals(payload, app.database.submissions().forOrder(assignment)!!.payload)
            assertEquals(1, app.database.work().events(assignment).count { it.type == "VOID" })
            shot("frozen-rejected")
        }
    }
    private fun selectOriginal(barcode: String) = onView(withText(containsString(barcode))).perform(scrollTo(), click())
    private fun confirmReason() {
        onView(withHint("实物核对及调整原因")).perform(replaceText("隔离合成实物核对：原包未装车，保留原事实"), closeSoftKeyboard())
        onView(withText("确认调整")).perform(click())
    }
    private fun broadcast(barcode: String) {
        app.sendBroadcast(Intent(ScannerReceiver.ACTION).putExtra(ScannerReceiver.BARCODE_KEY, barcode))
    }
    private fun awaitView(check: () -> Unit) {
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30)
        var last: Throwable? = null
        do { try { check(); return } catch (error: Throwable) { last = error }; Thread.sleep(100) } while (System.nanoTime() < deadline)
        throw AssertionError("Product page did not reach expected state", last)
    }
    private fun awaitState(condition: () -> Boolean) = awaitView { assertTrue(condition()) }
    private fun shot(name: String) {
        onView(isRoot()).perform(object : androidx.test.espresso.ViewAction {
            override fun getConstraints() = isRoot()
            override fun getDescription() = "Save the actual product view for isolated layout review"
            override fun perform(controller: androidx.test.espresso.UiController, view: android.view.View) {
                val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        })
    }
}
