package com.acrylic.outbound.pda

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.device.ScannerReceiver
import com.acrylic.outbound.pda.temporary.TemporaryDraftRepository
import com.acrylic.outbound.pda.ui.TemporaryDraftActivity
import com.acrylic.outbound.pda.ui.TemporaryWorkActivity
import org.hamcrest.CoreMatchers.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Signed fresh preparation; actual draft save and scan controls on the retained emulator. */
class TemporarySaveScanCombinationDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private lateinit var directory: File

    @Test fun savedPlanFeedsManualConfirmationAndPauseResume() {
        val args = InstrumentationRegistry.getArguments()
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(args.getString("temporarySaveScan") == "true")
        val run = requireNotNull(args.getString("evidenceRun"))
        require(run.matches(Regex("[a-zA-Z0-9-]{1,50}")))
        directory = File(app.filesDir, "temporary-save-scan/$run")
        check(directory.mkdirs()) { "Preserve previous evidence" }
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        File(directory, "database-before.json").writeText(DraftDiagnosticDatabaseSnapshot(app).read().toString(2))
        try {
            val resume = args.getString("resumeOrder")
            if (resume == null) exerciseNewDraft() else exerciseRetainedDraft(resume)
        } finally {
            File(directory, "database-after.json").writeText(DraftDiagnosticDatabaseSnapshot(app).read().toString(2))
        }
    }

    private fun exerciseRetainedDraft(id: String) {
        require(UUID.fromString(id).toString() == id)
        val saved = requireNotNull(app.database.temporaryDrafts().get(id))
        val pending = requireNotNull(app.database.temporaryScans().pending(id))
        assertEquals("SCANNING", saved.state)
        assertEquals(2L, saved.revision)
        assertEquals("MANUAL", pending.inputSource)
        assertEquals("WAITING_CONFIRM", pending.state)
        assertEquals(0L, app.temporaryStore().total(id).count)
        File(directory, "attempt-order-id.txt").writeText(id)
        scan(id, pending.barcode, true)
        assertNull(app.database.temporarySubmissions().forOrder(id))
        File(directory, "result.json").writeText(JSONObject().put("clientOrderId", id)
            .put("authorizationId", saved.authorizationId).put("planSha256", saved.planSha256)
            .put("revision", saved.revision).put("barcode", pending.barcode).put("count", 1)
            .put("resumedOriginalInput", true).put("noSubmission", true).toString(2))
    }

    private fun exerciseNewDraft() {
        val store = app.temporaryStore()
        val id = UUID.randomUUID().toString()
        val draft = TemporaryDraftRepository(store).create(id)
        File(directory, "attempt-order-id.txt").writeText(id)
        val code = store.source(id).bales.keys.first { app.database.temporaryScans().guard(it) == null }
        savePlan(id, code.take(3))
        val saved = requireNotNull(app.database.temporaryDrafts().get(id))
        assertEquals("SCANNING", saved.state)
        assertEquals(2L, saved.revision)
        assertEquals(2L, app.database.temporaryDrafts().lines(id).single().plannedCount)
        assertEquals(draft.authorizationId, saved.authorizationId)
        scan(id, code)
        assertEquals(1L, store.total(id).count)
        assertNull(app.database.temporarySubmissions().forOrder(id))
        File(directory, "result.json").writeText(JSONObject().put("clientOrderId", id).put("authorizationId", saved.authorizationId)
            .put("planSha256", saved.planSha256).put("revision", saved.revision).put("barcode", code)
            .put("count", store.total(id).count).put("noSubmission", true).toString(2))
    }

    private fun savePlan(id: String, product: String) {
        ActivityScenario.launch<TemporaryDraftActivity>(Intent(app, TemporaryDraftActivity::class.java).putExtra("clientOrderId", id)).use {
            await { onView(withId(R.id.add)).check(matches(isEnabled())) }
            onView(withId(R.id.vehicle)).perform(scrollTo(), replaceText("保存扫码隔离车"), closeSoftKeyboard())
            onView(withId(R.id.reason)).perform(scrollTo(), replaceText("合成资料受影响组合"), closeSoftKeyboard())
            onView(withId(R.id.productSearch)).perform(scrollTo(), replaceText(product), pressImeActionButton())
            onView(withId(R.id.count)).perform(scrollTo(), replaceText("1"), pressImeActionButton())
            onView(withId(R.id.add)).perform(scrollTo(), click())
            onView(withText("修改 $product 数量")).perform(scrollTo(), click())
            onView(isAssignableFrom(android.widget.EditText::class.java)).perform(replaceText("2"), closeSoftKeyboard())
            onView(withText("保存页面修改")).perform(click())
            onView(withId(R.id.save)).perform(scrollTo(), click())
            await {
                assertEquals(1L, app.database.temporaryDrafts().get(id)?.revision)
                onView(withId(R.id.save)).check(matches(isEnabled()))
                onView(withId(R.id.dirty)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            }
            shot("plan-saved")
            onView(withId(R.id.start)).perform(scrollTo(), click())
            await { assertTrue(app.temporaryWork.state.value.ready) }
        }
    }

    private fun scan(id: String, code: String, retained: Boolean = false) {
        ActivityScenario.launch<TemporaryWorkActivity>(Intent(app, TemporaryWorkActivity::class.java).putExtra("clientOrderId", id)).use { scenario ->
            await { assertTrue(app.temporaryWork.state.value.ready && app.temporaryWork.state.value.queued == 0) }
            if (!retained) enterManual(code)
            await {
                assertEquals("WAITING_CONFIRM", app.temporaryWork.state.value.candidate?.state)
                onView(withId(R.id.confirm)).check(matches(isEnabled()))
            }
            assertEquals(0L, app.temporaryStore().total(id).count)
            shot("manual-awaiting-confirmation")
            scenario.onActivity { activity ->
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
            await { assertEquals(1L, app.temporaryStore().total(id).count) }
            onView(withId(R.id.recentSaved)).check(matches(withText(containsString(code))))
            pauseAndResume(id, code)
            shot("duplicate-acknowledged")
            onView(withId(R.id.back)).perform(scrollTo(), click())
        }
    }

    private fun enterManual(code: String) {
        onView(withId(R.id.manualToggle)).perform(scrollTo(), click())
        onView(withId(R.id.manual)).perform(scrollTo(), replaceText(code))
        onView(withId(R.id.validate)).perform(scrollTo(), click())
    }

    private fun pauseAndResume(id: String, code: String) {
        val count = app.database.temporaryScans().captures(id).size
        onView(withId(R.id.pauseScan)).perform(scrollTo(), click())
        await { assertFalse(app.temporaryWork.state.value.ready) }
        app.sendBroadcast(Intent(ScannerReceiver.ACTION).putExtra(ScannerReceiver.BARCODE_KEY, code))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertEquals(count, app.database.temporaryScans().captures(id).size)
        assertEquals(1L, app.temporaryStore().total(id).count)
        onView(withId(R.id.pauseScan)).perform(scrollTo(), click())
        await { assertTrue(app.temporaryWork.state.value.ready) }
        app.sendBroadcast(Intent(ScannerReceiver.ACTION).putExtra(ScannerReceiver.BARCODE_KEY, code))
        await { assertEquals(count + 1, app.database.temporaryScans().captures(id).size) }
        await { onView(withId(R.id.confirm)).check(matches(withText(containsString("本包未保存")))) }
        assertEquals(1L, app.temporaryStore().total(id).count)
        shot("duplicate-rejected")
        onView(withId(R.id.confirm)).perform(scrollTo(), click())
        await { assertNull(app.temporaryWork.state.value.candidate) }
    }

    private fun await(check: () -> Unit) {
        val end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30)
        var last: Throwable? = null
        do {
            try { check(); return } catch (error: AssertionError) { last = error }
            Thread.sleep(100)
        } while (System.nanoTime() < end)
        throw AssertionError("Save/scan product flow did not finish", last)
    }

    private fun shot(name: String) {
        onView(isRoot()).perform(object : androidx.test.espresso.ViewAction {
            override fun getConstraints() = isRoot()
            override fun getDescription() = "Record isolated product screen"
            override fun perform(controller: androidx.test.espresso.UiController, view: android.view.View) {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        })
    }
}
