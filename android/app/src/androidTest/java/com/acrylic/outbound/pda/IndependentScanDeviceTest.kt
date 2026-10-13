package com.acrylic.outbound.pda

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import com.acrylic.outbound.pda.ui.IndependentTaskActivity
import com.acrylic.outbound.pda.ui.IndependentWorkActivity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import com.acrylic.outbound.pda.IndependentCredentialDeviceFixture as F
import com.acrylic.outbound.pda.IndependentScanDeviceFixture as S

class IndependentScanDeviceTest {
    @Test fun originalOfflineTaskThroughManualBroadcastFailureVoidAndRescan() {
        S.guard(); val id = checkNotNull(S.repository.latestRequest()).id
        val before = S.task(id); assertEquals(0L, before.state.actualCount); assertEquals("PLANNED", before.state.state)
        val bars = S.available(id)
        ActivityScenario.launch<IndependentTaskActivity>(Intent(F.app, IndependentTaskActivity::class.java)).use {
            F.message("独立计划已保存"); F.click(R.id.openIndependentWork); S.authorize()
            S.text(R.id.workManual, bars[0].lowercase()); F.click(R.id.workValidate)
            F.await { onView(withId(R.id.workConfirm)).check(matches(isEnabled())) }; assertEquals(0L, S.task(id).state.actualCount)
            F.click(R.id.workConfirm); S.count(id, 1); completeProduct(id, bars)
            onView(withId(R.id.workBack)).perform(independentSkuScroll()); F.click(R.id.workBack)
        }
        assertProductResult(id, before, bars[0])
    }
    @Test fun resumeOriginalProductAfterFirstManualSave() {
        S.guard(); val id = checkNotNull(S.repository.latestRequest()).id; val before = S.task(id)
        val original = F.app.database.independentScans().captures(id).single()
        assertEquals("COMMITTED", original.processing.state); assertEquals("MANUAL", original.capture.inputSource)
        assertEquals(1L, before.state.actualCount)
        val bars = listOf(original.capture.barcode, S.available(id).first())
        ActivityScenario.launch<IndependentWorkActivity>(intent(id)).use {
            S.authorize(); completeProduct(id, bars); F.click(R.id.workBack)
        }
        assertEquals(original, F.app.database.independentScans().capture(original.capture.id))
        assertProductResult(id, before, bars[0])
    }
    private fun completeProduct(id: String, bars: List<String>) {
        ready(); S.broadcast(bars[0]); failure(); F.click(R.id.workAcknowledge)
        ready(); S.broadcast(bars[1]); S.count(id, 2)
        ready(); S.broadcast("NOTINSKU001"); failure(); F.click(R.id.workAcknowledge); ready()
        voidDiscardAndRescan(id, bars[0])
    }
    private fun voidDiscardAndRescan(id: String, barcode: String) {
        F.click(R.id.workPause); S.text(R.id.workLocateBarcode, barcode); F.click(R.id.workLocate)
        F.await { onView(withId(R.id.workVoid)).check(matches(isEnabled())) }
        S.text(R.id.workReason, "Actual physical verification: unloaded original bale")
        F.click(R.id.workUnloaded); F.click(R.id.workVoid); S.count(id, 1)
        F.click(R.id.workPause); ready(); S.text(R.id.workManual, barcode); F.click(R.id.workValidate)
        F.await { onView(withId(R.id.workDiscard)).check(matches(isEnabled())) }
        F.click(R.id.workDiscard); ready(); S.broadcast(barcode); S.count(id, 2)
    }
    private fun assertProductResult(id: String, before: com.acrylic.outbound.pda.independent.IndependentTaskView, barcode: String) {
        val after = S.task(id); val dao = F.app.database.independentScans()
        val rows = dao.captures(id)
        assertEquals(before.task, after.task); assertEquals(before.lines, after.lines)
        assertEquals(6, rows.size); assertEquals(3, rows.count { it.processing.state == "COMMITTED" })
        assertEquals(2, rows.count { it.processing.state == "INVALID" && it.processing.failureAckAt != null })
        assertEquals(1, rows.count { it.processing.state == "DISCARDED" }); assertEquals(1, dao.voids(id).size)
        assertEquals(2L, dao.total(id).count); assertEquals("ACTIVE", dao.locate(id, barcode)?.status)
        File(S.directory, "product-task-id.txt").writeText(id)
        File(S.directory, "product-result.json").writeText(JSONObject().put("taskId", id).put("originalCredential", after.task.signedCredential)
            .put("captures", rows.size).put("effectiveCount", after.state.actualCount).put("weightGrams", after.state.weightGrams)
            .put("originalEmulatorKeystore", true).put("offlineProduct", true).toString())
    }
    @Test fun actualNewProcessReadsSameScansVoidsAndOriginalSequence() {
        S.guard(); val id = File(S.directory, "product-task-id.txt").readText(); val before = S.task(id)
        val originals = F.app.database.independentScans().captures(id)
        ActivityScenario.launch<IndependentWorkActivity>(intent(id)).use { scenario ->
            S.authorize(); S.count(id, 2)
            scenario.onActivity { IndependentSkuDeviceEvidence.capture(it, S.directory, "new-process-original-scans") }
        }
        assertEquals(before, S.task(id)); assertEquals(originals, F.app.database.independentScans().captures(id))
        File(S.directory, "new-process.json").writeText(JSONObject().put("taskId", id).put("sameOriginals", true).put("effectiveCount", 2).toString())
    }
    @Test fun actualSaveTailFailureKeepsOriginalConfirmedInput() {
        S.guard(); val id = File(F.app.filesDir, "independent-task-device/original-task-id.txt").readText()
        assertEquals(0L, S.task(id).state.actualCount); val barcode = S.available(id).first()
        val db = F.app.database.openHelper.writableDatabase
        db.execSQL("""CREATE TRIGGER native_independent_scan_tail BEFORE INSERT ON independent_task_event
            WHEN NEW.type='INDEPENDENT_SAVED' BEGIN SELECT RAISE(ABORT,'native scan tail'); END""")
        ActivityScenario.launch<IndependentWorkActivity>(intent(id)).use { scenario ->
            S.authorize(); S.broadcast(barcode)
            F.await { onView(withId(R.id.workBlocked)).check(matches(withText(org.hamcrest.CoreMatchers.containsString("native scan tail")))) }
            scenario.onActivity { IndependentSkuDeviceEvidence.capture(it, S.directory, "save-tail-failure") }
        }
        val row = checkNotNull(F.app.database.independentScans().pending(id))
        assertEquals("CONFIRM_REQUESTED", row.processing.state); assertEquals(0L, S.task(id).state.actualCount)
        assertNull(F.app.database.independentScans().scan(row.capture.id)); assertNull(F.app.database.independentScans().guard(barcode))
        File(S.directory, "tail-input.json").writeText(JSONObject().put("taskId", id).put("captureId", row.capture.id)
            .put("barcode", barcode).put("originalCredential", S.task(id).task.signedCredential).toString())
    }
    @Test fun realNewProcessRecoversTailAlongOriginalCaptureId() {
        S.guard(); val original = JSONObject(File(S.directory, "tail-input.json").readText()); val id = original.getString("taskId")
        F.app.database.openHelper.writableDatabase.execSQL("DROP TRIGGER native_independent_scan_tail")
        ActivityScenario.launch<IndependentWorkActivity>(intent(id)).use { S.authorize(); S.count(id, 1) }
        val dao = F.app.database.independentScans(); val c = dao.captures(id)
        assertEquals(1, c.size); assertEquals(original.getString("captureId"), c.single().capture.id)
        assertNotNull(dao.scan(c.single().capture.id)); assertEquals(original.getString("originalCredential"), S.task(id).task.signedCredential)
        File(S.directory, "tail-recovery.json").writeText(original.put("newProcess", true).put("exactlyOnce", true).toString())
    }
    @Test fun actualSmallScreenLargeTextAndKeyboardRemainReachable() {
        S.guard(); val id = File(S.directory, "product-task-id.txt").readText()
        ActivityScenario.launch<IndependentWorkActivity>(intent(id)).use { scenario ->
            S.authorize(); val scale = F.app.resources.configuration.fontScale.toString()
            scenario.onActivity { activity ->
                (activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as android.widget.ScrollView).scrollTo(0, 0)
            }
            F.await { scenario.onActivity { activity ->
                assertEquals(0, (activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as android.widget.ScrollView).scrollY)
            } }
            scenario.onActivity { IndependentSkuDeviceEvidence.capture(it, S.directory, "layout-$scale-top") }
            onView(withId(R.id.workReason)).perform(independentSkuScroll(), click())
            F.await { scenario.onActivity { activity ->
                assertTrue(androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)
                    ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true)
            } }
            scenario.onActivity { IndependentSkuDeviceEvidence.capture(it, S.directory, "layout-$scale-keyboard") }
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            onView(withId(R.id.workBack)).perform(independentSkuScroll()).check(matches(isDisplayed()))
            scenario.onActivity { IndependentSkuDeviceEvidence.capture(it, S.directory, "layout-$scale-bottom") }
        }
    }
    private fun intent(id: String) = Intent(F.app, IndependentWorkActivity::class.java).putExtra("taskId", id)
    private fun ready() = F.await { onView(withId(R.id.workManual)).check(matches(isEnabled())) }
    private fun failure() = F.await { onView(withId(R.id.workAcknowledge)).check(matches(isEnabled())) }
}
