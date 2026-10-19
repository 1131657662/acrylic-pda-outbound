package com.acrylic.outbound.pda

import android.content.Intent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.ui.TemporaryDetailsActivity
import com.acrylic.outbound.pda.ui.WorkDetailsActivity
import org.hamcrest.Matchers.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Consumes earlier real-writer synthetic records without reseeding or replacing their identity. */
class RetainedDetailsStateDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private lateinit var evidence: File
    private lateinit var ui: FormalDetailsDeviceUi
    private lateinit var scenario: ActivityScenario<ComponentActivity>

    @Test fun retainedStatePagesRemainReadOnlyAcrossFiltersLocatorAndRecreation() {
        guard()
        val before = DraftDiagnosticDatabaseSnapshot(app).read().toString(2)
        val session = app.session.requireSession().toString()
        File(evidence, "database-before.json").writeText(before)
        temporary(); formal(); missingOrders()
        val after = DraftDiagnosticDatabaseSnapshot(app).read().toString(2)
        File(evidence, "database-after.json").writeText(after)
        assertEquals(before, after)
        assertEquals(session, app.session.requireSession().toString())
        File(evidence, "result.json").writeText(JSONObject().put("roomUnchanged", true)
            .put("identityUnchanged", true).put("formalFrozen", true).put("temporaryVoidRows", 27)
            .put("fontScale", app.resources.configuration.fontScale).put("originalIdentityOrCustodyProven", false).toString(2))
    }

    private fun guard() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("retainedDetails") == "synthetic-914")
        check(app.session.settings.server == "http://127.0.0.1:19781/api/v1/")
        assertEquals(args.getString("expectedFontScale")!!.toFloat(), app.resources.configuration.fontScale, .01f)
        val run = checkNotNull(args.getString("evidenceRun")); check(run.matches(Regex("[a-z0-9-]{1,40}")))
        evidence = File(app.filesDir, "retained-details/$run"); check(evidence.mkdirs())
        ui = FormalDetailsDeviceUi(evidence)
        app.work.pause(); app.temporaryWork.pause()
    }

    private fun temporary() {
        val id = File(app.filesDir, "adjustment-filter/order.txt").readText().trim()
        val scans = app.database.temporaryScans().allScans(id)
        assertEquals((1L..27L).toList(), scans.map { it.orderScanSeq })
        assertTrue(scans.all { it.status == "VOID" })
        launch(TemporaryDetailsActivity::class.java, "clientOrderId", id) {
            rows(1, 20); select("已撤销"); rows(1, 20)
            ui.click(R.id.next); rows(21, 7)
            onView(withId(R.id.next)).check(matches(not(isEnabled())))
            scenario.recreate(); rows(21, 7)
            onView(withId(R.id.filter)).check(matches(withSpinnerText("已撤销")))
            ui.click(R.id.previous); rows(1, 20)
            select("已保存原记录"); empty(); select("未处理输入"); empty()
            select("全部原记录"); rows(1, 20)
            val code = scans.first().barcode; val last = scans.last { it.barcode == code }.orderScanSeq
            locate(code, "完整条码"); rows(last, (28 - last).toInt())
            onView(withId(R.id.filter)).check(matches(withSpinnerText("全部原记录")))
            locate("27", "原序号"); rows(27, 1)
            ui.inspect("temporary-last"); invalidThenRecover(1, 20)
        }
    }

    private fun formal() {
        val original = JSONObject(File(app.filesDir, "formal-adjustment-filter/result.json").readText())
        val id = original.getString("assignmentId")
        val frozen = checkNotNull(app.database.submissions().forOrder(id))
        assertEquals(original.getString("submissionId"), frozen.id)
        assertEquals("FROZEN", frozen.state)
        launch(WorkDetailsActivity::class.java, "assignmentId", id) {
            rows(1, 1); select("已保存原记录"); rows(1, 1)
            ui.inspect("formal-saved")
            scenario.recreate(); rows(1, 1)
            onView(withId(R.id.filter)).check(matches(withSpinnerText("已保存原记录")))
            select("已撤销"); empty(); select("未处理输入"); empty()
            locate("1", "原序号"); rows(1, 1)
            onView(withId(R.id.adjustPackages)).check(matches(not(isEnabled())))
            invalidThenRecover(1, 1)
        }
        assertEquals(frozen.payload, app.database.submissions().forOrder(id)!!.payload)
    }

    private fun missingOrders() {
        listOf(WorkDetailsActivity::class.java to "assignmentId", TemporaryDetailsActivity::class.java to "clientOrderId").forEach { (type, key) ->
            launch(type, key, "00000000-0000-0000-0000-000000000000") {
                ui.await { onView(withId(R.id.status)).check(matches(withText(containsString("读取未完成")))) }
                scenario.onActivity { assertEquals(0, it.findViewById<LinearLayout>(R.id.rows).childCount) }
                onView(withId(R.id.adjustPackages)).check(matches(not(isEnabled())))
            }
        }
    }

    private fun launch(type: Class<out ComponentActivity>, key: String, id: String, check: () -> Unit) {
        ActivityScenario.launch<ComponentActivity>(Intent(app, type).putExtra(key, id)).use {
            scenario = it; check(); ui.click(R.id.back)
        }
    }

    private fun select(label: String) {
        ui.click(R.id.filter); onData(equalTo(label)).perform(click())
    }

    private fun rows(first: Long, count: Int) {
        ui.await {
            onView(withId(R.id.retry)).check(matches(isEnabled()))
            scenario.onActivity {
                val rows = it.findViewById<LinearLayout>(R.id.rows)
                assertEquals(count, rows.childCount)
                assertTrue((rows.getChildAt(0) as TextView).text.startsWith("原序号 $first ·"))
            }
        }
    }

    private fun empty() {
        ui.await { onView(withId(R.id.status)).check(matches(withText(containsString("暂无匹配记录")))) }
        scenario.onActivity { assertEquals(0, it.findViewById<LinearLayout>(R.id.rows).childCount) }
        onView(withId(R.id.previous)).check(matches(not(isEnabled())))
        onView(withId(R.id.next)).check(matches(not(isEnabled())))
    }

    private fun locate(value: String, mode: String) {
        ui.click(R.id.locateMode); onData(equalTo(mode)).perform(click())
        ui.reveal(R.id.locateInput); onView(withId(R.id.locateInput)).perform(replaceText(value))
        androidx.test.espresso.Espresso.closeSoftKeyboard(); ui.click(R.id.locate)
    }

    private fun invalidThenRecover(first: Long, count: Int) {
        locate("0", "原序号")
        ui.await { onView(withId(R.id.status)).check(matches(withText(containsString("请输入有效原序号")))) }
        scenario.onActivity { assertEquals(0, it.findViewById<LinearLayout>(R.id.rows).childCount) }
        ui.click(R.id.first); rows(first, count)
    }
}
