package com.acrylic.outbound.pda

import android.content.Intent
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.ui.ScanAdjustmentActivity
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.File

/** Native keyboard consumer; retained scan facts and authority are never changed. */
class AdjustmentFilterNativeImeDeviceTest {
    private val fixture = FormalStatusDeviceFixture()
    private lateinit var ui: AdjustmentFilterNativeImeUi
    private lateinit var evidence: File
    private lateinit var scenario: ActivityScenario<ScanAdjustmentActivity>

    @Test fun nativeKeyboardKeepsFilterActionsReachableWithoutChangingOriginals() {
        evidence = fixture.guard(); ui = AdjustmentFilterNativeImeUi(evidence)
        val args = InstrumentationRegistry.getArguments()
        assertEquals(args.getString("expectedFontScale")!!.toFloat(), fixture.app.resources.configuration.fontScale, .01f)
        val before = fixture.snapshot(); val identity = fixture.app.session.requireSession().toString()
        val id = File(fixture.root, "assignment.txt").readText().trim()
        File(evidence, "database-before.json").writeText(before)
        try {
            ActivityScenario.launch<ScanAdjustmentActivity>(Intent(fixture.app, ScanAdjustmentActivity::class.java)
                .putExtra("assignmentId", id)).use {
                scenario = it; rows((1L..20L).toList())
                enter("22"); capture("keyboard-valid")
                ui.click(R.id.filterRecords); ime(false); rows(listOf(22L))
                scenario.recreate(); rows(listOf(22L))
                enter("0"); capture("keyboard-invalid")
                ui.click(R.id.filterRecords); rows(emptyList())
                ui.click(R.id.clearFilter); ime(false); rows((1L..20L).toList())
                capture("cleared"); ui.click(R.id.back)
            }
            assertEquals(before, fixture.snapshot()); assertEquals(identity, fixture.app.session.requireSession().toString())
            File(evidence, "result.json").writeText(JSONObject().put("nativeIme", true).put("roomUnchanged", true)
                .put("sessionContentUnchanged", true).put("fontScale", fixture.app.resources.configuration.fontScale)
                .put("adjustmentWritten", false).put("localDeletionAllowed", false).toString(2))
        } finally { File(evidence, "database-after.json").writeText(fixture.snapshot()) }
    }

    private fun enter(value: String) {
        ui.reveal(R.id.sequence)
        onView(withId(R.id.sequence)).perform(click())
        ime(true); ui.reveal(R.id.sequence)
        onView(withId(R.id.sequence)).perform(clearText(), typeText(value))
        ime(true); ui.reveal(R.id.sequence)
    }

    private fun ime(expected: Boolean) = ui.await {
        scenario.onActivity {
            assertEquals(expected, ViewCompat.getRootWindowInsets(it.window.decorView)!!.isVisible(WindowInsetsCompat.Type.ime()))
        }
    }

    private fun rows(expected: List<Long>) = ui.await {
        onView(withId(R.id.filterRecords)).check(matches(isEnabled()))
        scenario.onActivity { activity ->
            val views = activity.findViewById<LinearLayout>(R.id.rows)
            val actual = (0 until views.childCount).map { (views.getChildAt(it) as Button).text.toString().substringBefore('.').toLong() }
            assertEquals(expected, actual)
            if (expected.isEmpty()) assertTrue(activity.findViewById<android.widget.TextView>(R.id.status).text.contains("请输入有效原序号"))
        }
    }

    private fun capture(name: String) = scenario.onActivity { activity -> ui.capture(activity, name) }
}
