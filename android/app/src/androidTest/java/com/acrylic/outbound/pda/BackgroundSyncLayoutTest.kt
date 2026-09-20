package com.acrylic.outbound.pda

import android.graphics.Rect
import android.view.WindowManager
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.ui.DeviceSettingsActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Run at normal, 200% font and low-height emulator configurations; all actions are read-only. */
@RunWith(AndroidJUnit4::class)
class BackgroundSyncLayoutTest {
    @Test fun retainedSyncStatusAndActionsRemainReadable() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
        val facts = BackgroundLifecycleFacts(app)
        check(InstrumentationRegistry.getArguments().getString("retainedBackground") == "true")
        facts.assertPreserved()
        val evidence = JSONArray()
        ActivityScenario.launch(DeviceSettingsActivity::class.java).use { scenario ->
            awaitStatus(scenario)
            for (id in listOf(R.id.syncStatus, R.id.sound, R.id.checkConnection, R.id.appUpdate, R.id.logout, R.id.back)) {
                scenario.onActivity { activity ->
                    val view = activity.findViewById<TextView>(id)
                    val scroll = view.parent.parent as android.widget.ScrollView
                    scroll.scrollTo(0, view.top)
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                onView(withId(id)).check(matches(isDisplayed()))
                scenario.onActivity { activity -> evidence.put(checkField(activity, id)) }
            }
        }
        val label = InstrumentationRegistry.getArguments().getString("layoutCase") ?: "normal"
        check(label in setOf("normal", "large-font", "low-height", "combined"))
        File(facts.directory, "layout-$label.json").writeText(evidence.toString())
        facts.assertPreserved()
    }

    private fun checkField(activity: DeviceSettingsActivity, id: Int): JSONObject {
        val view = activity.findViewById<TextView>(id)
        val layout = checkNotNull(view.layout)
        val name = activity.resources.getResourceEntryName(id)
        assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        assertTrue("Vertical clipping: $name", layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
        for (line in 0 until layout.lineCount) {
            assertEquals("Ellipsized: $name", 0, layout.getEllipsisCount(line))
            assertTrue("Horizontal clipping: $name", layout.getLineWidth(line) <= layout.width + 1)
        }
        val visible = Rect()
        assertTrue(view.getGlobalVisibleRect(visible))
        assertEquals("Not fully visible after scroll: $name", view.height, visible.height())
        if (id != R.id.syncStatus) assertTrue(view.height >= 48 * activity.resources.displayMetrics.density)
        return JSONObject().put("id", name).put("text", view.text).put("width", view.width).put("height", view.height)
            .put("fontScale", activity.resources.configuration.fontScale).put("screenHeightDp", activity.resources.configuration.screenHeightDp)
    }

    private fun awaitStatus(scenario: ActivityScenario<DeviceSettingsActivity>) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 5000
        var ready = false
        while (!ready && android.os.SystemClock.elapsedRealtime() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { ready = it.findViewById<TextView>(R.id.syncStatus).text.contains("订单、条码和操作记录已同步") }
            if (!ready) Thread.sleep(50)
        }
        assertTrue("Persisted success message should be shown", ready)
    }
}
