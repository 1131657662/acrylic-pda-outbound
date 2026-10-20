package com.acrylic.outbound.pda

import android.content.Intent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.ui.AdjustmentRecords
import com.acrylic.outbound.pda.ui.ScanAdjustmentActivity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Retained TEMP originals; native IME actions never bypass the reboot authority fence. */
class TemporaryAdjustmentNativeImeDeviceTest {
    private val fixture = AdjustmentStatusDeviceFixture()
    private lateinit var ui: AdjustmentFilterNativeImeUi
    private lateinit var evidence: File
    private lateinit var scenario: ActivityScenario<ScanAdjustmentActivity>
    private val blocked = "设备已重启，请联网校验授权时间"

    @Test fun nativeKeyboardKeepsTemporaryFilterReachableAndAuthorityBlocked() {
        evidence = fixture.guard(); ui = AdjustmentFilterNativeImeUi(evidence)
        val scale = InstrumentationRegistry.getArguments().getString("expectedFontScale")!!.toFloat()
        assertEquals(scale, fixture.app.resources.configuration.fontScale, .01f)
        val id = File(fixture.root, "order.txt").readText().trim()
        assertEquals(blocked, AdjustmentRecords(fixture.app, "", id).blockReason())
        val before = fixture.snapshot(); val identity = fixture.app.session.requireSession().toString()
        File(evidence, "database-before.json").writeText(before)
        try {
            ActivityScenario.launch<ScanAdjustmentActivity>(Intent(fixture.app, ScanAdjustmentActivity::class.java)
                .putExtra("clientOrderId", id)).use {
                scenario = it; exercise()
            }
            assertEquals(before, fixture.snapshot()); assertEquals(identity, fixture.app.session.requireSession().toString())
            File(evidence, "result.json").writeText(JSONObject().put("nativeIme", true).put("roomUnchanged", true)
                .put("sessionContentUnchanged", true).put("fontScale", scale)
                .put("adjustmentBlockReason", blocked).put("allAdjustmentRowsBlocked", true)
                .put("adjustmentWritten", false).put("localDeletionAllowed", false).toString(2))
        } finally { File(evidence, "database-after.json").writeText(fixture.snapshot()) }
    }

    private fun exercise() {
        rows((1L..20L).toList())
        enter("22"); capture("keyboard-valid")
        ui.click(R.id.filterRecords); ime(false); rows(listOf(22L))
        scenario.recreate(); rows(listOf(22L)); authorityVisible()
        enter("0"); capture("keyboard-invalid")
        ui.click(R.id.filterRecords); rows(emptyList())
        onView(withId(R.id.status)).check(matches(withText("请输入有效原序号")))
        ui.click(R.id.clearFilter); ime(false); rows((1L..20L).toList()); authorityVisible()
        capture("cleared"); ui.click(R.id.back)
    }

    private fun enter(value: String) {
        ui.reveal(R.id.sequence); onView(withId(R.id.sequence)).perform(click())
        ime(true); ui.reveal(R.id.sequence)
        onView(withId(R.id.sequence)).perform(clearText(), typeText(value))
        ime(true); ui.reveal(R.id.sequence)
    }

    private fun rows(expected: List<Long>) = ui.await {
        onView(withId(R.id.filterRecords)).check(matches(isEnabled()))
        scenario.onActivity { activity ->
            val views = activity.findViewById<LinearLayout>(R.id.rows)
            val buttons = (0 until views.childCount).map { views.getChildAt(it) as Button }
            assertEquals(expected, buttons.map { it.text.toString().substringBefore('.').toLong() })
            assertTrue(buttons.all { !it.isEnabled })
        }
    }

    private fun authorityVisible() = scenario.onActivity {
        assertTrue(it.findViewById<TextView>(R.id.status).text.contains(blocked))
    }

    private fun ime(expected: Boolean) = ui.await {
        scenario.onActivity {
            assertEquals(expected, ViewCompat.getRootWindowInsets(it.window.decorView)!!.isVisible(WindowInsetsCompat.Type.ime()))
        }
    }

    private fun capture(name: String) = scenario.onActivity { ui.capture(it, name) }
}
