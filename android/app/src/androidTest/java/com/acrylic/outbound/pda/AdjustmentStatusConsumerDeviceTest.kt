package com.acrylic.outbound.pda

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.Button
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.ui.AdjustmentRecords
import com.acrylic.outbound.pda.ui.ScanAdjustmentActivity
import org.hamcrest.Matchers.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Actual product spinner/page consumer; no direct Room inserts or canceled activation path. */
class AdjustmentStatusConsumerDeviceTest {
    private val fixture = AdjustmentStatusDeviceFixture()
    private lateinit var ui: FormalDetailsDeviceUi
    private lateinit var scenario: ActivityScenario<ScanAdjustmentActivity>
    private lateinit var evidence: File
    private val expectedBlockReason = InstrumentationRegistry.getArguments()
        .getString("expectedAdjustmentBlockReason").orEmpty()

    @Test fun createSyntheticOriginalsOnceThroughProductWriters() {
        evidence = fixture.guard()
        try { fixture.createOriginals(evidence) }
        finally { File(evidence, "database-final.json").writeText(fixture.snapshot()) }
    }

    @Test fun approvedStatusOptionsPreserveOriginalFactsAcrossPagingAndRecreation() {
        evidence = fixture.guard(); ui = FormalDetailsDeviceUi(evidence)
        val id = File(fixture.root, "order.txt").readText().trim()
        check(expectedBlockReason in setOf("", "设备已重启，请联网校验授权时间"))
        assertEquals(expectedBlockReason, AdjustmentRecords(fixture.app, "", id).blockReason())
        val rows = fixture.app.database.temporaryScans().allScans(id)
        assertEquals((1L..22L).toList(), rows.map { it.orderScanSeq })
        assertEquals(20, rows.count { it.status == "VOID" }); assertEquals(2, rows.count { it.status == "ACTIVE" })
        val before = fixture.snapshot(); val identity = fixture.app.session.requireSession().toString()
        File(evidence, "database-before.json").writeText(before)
        try {
            ActivityScenario.launch<ScanAdjustmentActivity>(Intent(fixture.app, ScanAdjustmentActivity::class.java)
                .putExtra("clientOrderId", id)).use {
                scenario = it; allRows(); statusOptions(); combinedFilter(rows.last().deliveryCode)
                ui.click(R.id.back)
            }
            assertEquals(before, fixture.snapshot())
            assertEquals(identity, fixture.app.session.requireSession().toString())
            File(evidence, "result.json").writeText(JSONObject().put("roomUnchanged", true).put("identityUnchanged", true)
                .put("actualProductSpinner", true).put("retainedPositiveInputAvailable", false)
                .put("adjustmentBlockReason", expectedBlockReason).put("allAdjustmentRowsBlocked", expectedBlockReason.isNotBlank())
                .put("fontScale", fixture.app.resources.configuration.fontScale).put("localDeletionAllowed", false).toString(2))
        } finally { File(evidence, "database-after.json").writeText(fixture.snapshot()) }
    }

    private fun allRows() {
        rows((1L..20L).toList(), true)
        onView(withId(R.id.statusFilter)).check(matches(withSpinnerText("全部")))
        ui.click(R.id.next); rows(listOf(21L, 22L), false)
        ui.click(R.id.previous); rows((1L..20L).toList(), true)
    }

    private fun statusOptions() {
        select("有效"); rows(listOf(21L, 22L), false)
        select("已撤销"); rows((1L..20L).toList(), false)
        scenario.recreate(); rows((1L..20L).toList(), false)
        onView(withId(R.id.statusFilter)).check(matches(withSpinnerText("已撤销")))
        saveView("void-exact-page")
        select("主管保留"); rows(emptyList(), false)
        statusMessage("没有匹配的原记录（主管保留）")
        saveView("retained-empty")
        ui.click(R.id.clearFilter); rows((1L..20L).toList(), true)
        onView(withId(R.id.statusFilter)).check(matches(withSpinnerText("全部")))
    }

    private fun combinedFilter(product: String) {
        select("有效")
        replace(R.id.sequence, "22"); replace(R.id.productFilter, product)
        ui.click(R.id.filterRecords); rows(listOf(22L), false)
        scenario.recreate(); rows(listOf(22L), false)
        onView(withId(R.id.statusFilter)).check(matches(withSpinnerText("有效")))
        replace(R.id.sequence, "0"); ui.click(R.id.filterRecords); rows(emptyList(), false)
        scenario.recreate(); rows(emptyList(), false)
        statusMessage("请输入有效原序号")
        ui.click(R.id.clearFilter); rows((1L..20L).toList(), true)
    }

    private fun select(label: String) {
        ui.click(R.id.statusFilter); onData(equalTo(label)).perform(click())
        ui.click(R.id.filterRecords)
    }

    private fun statusMessage(normal: String) {
        val expected = expectedBlockReason.ifBlank { normal }
        onView(withId(R.id.status)).check(matches(withText(containsString(expected))))
    }

    private fun replace(id: Int, value: String) {
        ui.reveal(id); onView(withId(id)).perform(replaceText(value), closeSoftKeyboard())
    }

    private fun rows(expected: List<Long>, more: Boolean) {
        ui.await {
            onView(withId(R.id.filterRecords)).check(matches(isEnabled()))
            scenario.onActivity { activity ->
                val views = activity.findViewById<LinearLayout>(R.id.rows)
                val actual = (0 until views.childCount).map { (views.getChildAt(it) as Button).text.toString().substringBefore('.').toLong() }
                assertEquals(expected, actual)
                assertEquals(more, activity.findViewById<Button>(R.id.next).isEnabled)
                if (expected.isEmpty() || expected.first() == 1L) assertFalse(activity.findViewById<Button>(R.id.previous).isEnabled)
                if (expectedBlockReason.isNotBlank() || (expected.isNotEmpty() && expected.last() <= 20L))
                    assertTrue((0 until views.childCount).all { !views.getChildAt(it).isEnabled })
            }
        }
    }

    private fun saveView(label: String) {
        scenario.onActivity { activity ->
            val root = activity.window.decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            try {
                root.draw(Canvas(bitmap))
                File(evidence, "$label.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { bitmap.recycle() }
        }
    }
}
