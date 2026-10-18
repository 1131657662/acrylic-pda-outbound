package com.acrylic.outbound.pda

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.ui.ScanAdjustmentActivity
import org.hamcrest.CoreMatchers.containsString
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Consumes the retained synthetic original exactly once through the real confirmation dialog. */
class AdjustmentFilterConfirmationDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val directory get() = File(app.filesDir, "adjustment-filter-confirmation")
    private val reason = "隔离测试实物未装车，保留原序号及原输入"

    @Test fun filteredOriginalCanBeConfirmedWithLargeTextWithoutAddingCapture() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("adjustmentFilter") == "true")
        check(app.resources.configuration.fontScale >= 2f)
        check(directory.mkdirs()) { "Preserve earlier confirmation attempt" }
        val id = File(app.filesDir, "adjustment-filter/order.txt").readText().trim()
        val dao = app.database.temporaryScans()
        val captures = dao.captures(id); val scans = dao.allScans(id); val events = dao.events(id)
        val original = scans.single { it.status == "ACTIVE" }
        assertEquals(27L, original.orderScanSeq)
        val before = DraftDiagnosticDatabaseSnapshot(app).read().toString(2)
        File(directory, "database-before.json").writeText(before)
        confirmAtLargeText(id, before)
        assertEquals(captures, dao.captures(id))
        assertEquals(scans.map { if (it == original) it.copy(status = "VOID") else it }, dao.allScans(id))
        assertNull(dao.guard(original.barcode)); assertEquals(0L, dao.guardCount(id))
        assertEquals(0L, dao.total(id).count); assertEquals(0L, dao.total(id).grams)
        assertTrue(dao.products(id).isEmpty())
        val afterEvents = dao.events(id)
        assertEquals(events, afterEvents.dropLast(1)); assertEquals(events.size + 1, afterEvents.size)
        assertEquals("VOID", afterEvents.last().type); assertEquals(reason, afterEvents.last().detail)
        assertEquals(original.captureId, afterEvents.last().captureId)
        File(directory, "database-after.json").writeText(DraftDiagnosticDatabaseSnapshot(app).read().toString(2))
        File(directory, "result.json").writeText(JSONObject().put("clientOrderId", id)
            .put("captureId", original.captureId).put("originalSequence", 27).put("newCaptures", 0)
            .put("voidEventsAdded", 1).put("localDeletionAllowed", false).toString(2))
    }

    private fun confirmAtLargeText(id: String, before: String) {
        val intent = Intent(app, ScanAdjustmentActivity::class.java).putExtra("clientOrderId", id)
        ActivityScenario.launch<ScanAdjustmentActivity>(intent).use { scenario ->
            selectOriginal()
            confirmOriginal(before)
            await { onView(withId(R.id.status)).check(matches(withText(containsString("调整已保存到本机")))) }
            scenario.recreate()
            await { onView(withId(R.id.filterRecords)).check(matches(isEnabled())) }
            onView(withText(containsString("27. "))).check(matches(org.hamcrest.CoreMatchers.not(isEnabled())))
        }
    }

    private fun selectOriginal() {
        await { onView(withId(R.id.filterRecords)).check(matches(isEnabled())) }
        onView(withId(R.id.sequence)).perform(independentSkuScroll(), replaceText("27"), closeSoftKeyboard())
        onView(withId(R.id.filterRecords)).perform(independentSkuScroll(), click())
        await { onView(withId(R.id.filterRecords)).check(matches(isEnabled())) }
        onView(withText(containsString("27. "))).perform(independentSkuScroll(), click())
        AdjustmentFilterDialogEvidence.save(directory)
    }

    private fun confirmOriginal(before: String) {
        onView(withText("确认调整")).inRoot(isDialog()).perform(click())
        onView(withId(R.id.reason)).inRoot(isDialog()).check(matches(hasErrorText("请填写1～500字实物核对原因")))
        assertEquals(before, DraftDiagnosticDatabaseSnapshot(app).read().toString(2))
        onView(withId(R.id.reason)).inRoot(isDialog())
            .perform(independentSkuScroll(), replaceText(reason), closeSoftKeyboard())
        AdjustmentFilterDialogEvidence.save(directory)
        onView(withText("确认调整")).inRoot(isDialog()).perform(click())
    }

    private fun await(check: () -> Unit) {
        val end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30)
        var last: AssertionError? = null
        do {
            try { check(); return } catch (error: AssertionError) { last = error }
            Thread.sleep(50)
        } while (System.nanoTime() < end)
        throw AssertionError("Filtered confirmation did not reach expected state", last)
    }
}
