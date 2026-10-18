package com.acrylic.outbound.pda

import android.content.Intent
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.ui.ScanAdjustmentActivity
import org.hamcrest.CoreMatchers.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Same synthetic native device, real HTTP publication/claim/prepare, and actual formal lookup page. */
class FormalAdjustmentFilterDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val directory get() = File(app.filesDir, "formal-adjustment-filter")

    @Test fun formalProductFiltersRetainOriginalFactsAndFrozenRowsStayDisabled() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("adjustmentFilter") == "true")
        check(directory.mkdirs()) { "Preserve earlier facts" }
        val input = JSONObject(File(app.filesDir, "high-frequency/private/input.json").readText())
        val web = FaultRestartWebClient(input.getString("baseUrl"), input); web.login()
        val draft = File(app.filesDir, "adjustment-filter/order.txt").readText().trim()
        val store = app.temporaryStore(); store.load(draft)
        val code = store.source(draft).bales.keys.first { app.database.temporaryScans().guard(it) == null }
        val assignment = prepare(web, code.take(3))
        save(assignment, code)
        val before = DraftDiagnosticDatabaseSnapshot(app).read().toString(2)
        File(directory, "database-before.json").writeText(before)
        inspect(assignment, code.take(3), false)
        val after = DraftDiagnosticDatabaseSnapshot(app).read().toString(2)
        File(directory, "database-after.json").writeText(after)
        assertEquals(before, after)
        val frozen = app.freezer.freeze(assignment, "")
        inspect(assignment, code.take(3), true)
        assertEquals(frozen.payload, app.database.submissions().forOrder(assignment)!!.payload)
        File(directory, "result.json").writeText(JSONObject().put("assignmentId", assignment)
            .put("submissionId", frozen.id).put("state", "FROZEN_UNSENT").put("frozenDisabled", true)
            .put("lookupChangedDatabase", false).put("localDeletionAllowed", false).toString(2))
    }

    private fun prepare(web: FaultRestartWebClient, product: String): String {
        val baseline = web.get("inventory/current")
        val plan = JSONObject().put("vehicleNo", "正式筛选隔离车").put("containerNo", "").put("notes", "合成筛选验证")
            .put("expectedActiveSnapshot", baseline.get("activeSnapshotId"))
            .put("expectedMappingRevision", baseline.get("mappingRevision"))
        plan.put("lines", org.json.JSONArray().put(JSONObject().put("deliveryCode", product).put("plannedCount", 1)))
        val id = web.post("orders", plan).getString("orderId")
        web.post("orders/$id/publish", JSONObject().put("expectedVersion", "1")
            .put("expectedPolicyVersion", "1").put("reason", "合成正式筛选入口验证"))
        val order = app.repository.list("pda/orders", "cursor").single { it.getString("id") == id }
        return app.repository.prepare(app.repository.claim(order, "合成正式扫码")) {}.assignmentId
    }

    private fun save(assignment: String, code: String) {
        app.work.resume(assignment)
        await { assertTrue(app.work.state.value.ready) }
        assertTrue(app.work.receive(code, "MANUAL"))
        await { assertEquals("WAITING_CONFIRM", app.work.state.value.candidate?.state) }
        app.work.confirm(app.work.state.value.candidate!!.id)
        await { assertEquals(1L, app.database.scans().total(assignment).count); assertEquals(0, app.work.state.value.queued) }
        app.work.pause()
    }

    private fun inspect(id: String, product: String, frozen: Boolean) {
        ActivityScenario.launch<ScanAdjustmentActivity>(Intent(app, ScanAdjustmentActivity::class.java).putExtra("assignmentId", id)).use { scenario ->
            await { onView(withId(R.id.filterRecords)).check(matches(isEnabled())) }
            onView(withId(R.id.sequence)).perform(independentSkuScroll(), replaceText("1"), closeSoftKeyboard())
            onView(withId(R.id.productFilter)).perform(independentSkuScroll(), replaceText(product), closeSoftKeyboard())
            onView(withId(R.id.filterRecords)).perform(independentSkuScroll(), click())
            await { onView(withId(R.id.filterRecords)).check(matches(isEnabled())) }
            scenario.onActivity { activity ->
                val rows = activity.findViewById<LinearLayout>(R.id.rows)
                assertEquals(1, rows.childCount)
                val row = rows.getChildAt(0) as android.widget.Button
                assertTrue(row.text.toString().startsWith("1. ")); assertEquals(!frozen, row.isEnabled)
            }
            if (frozen) onView(withId(R.id.status)).check(matches(withText(containsString("冻结或停止"))))
            onView(withId(R.id.productFilter)).perform(independentSkuScroll(), replaceText("%_'") , closeSoftKeyboard())
            onView(withId(R.id.filterRecords)).perform(independentSkuScroll(), click())
            await { scenario.onActivity { assertEquals(0, it.findViewById<LinearLayout>(R.id.rows).childCount) } }
        }
    }

    private fun await(check: () -> Unit) {
        val end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30)
        var last: AssertionError? = null
        do {
            try { check(); return } catch (error: AssertionError) { last = error }
            Thread.sleep(50)
        } while (System.nanoTime() < end)
        throw AssertionError("Formal original filter did not reach state", last)
    }
}
