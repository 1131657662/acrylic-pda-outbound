package com.acrylic.outbound.pda

import android.content.Intent
import android.widget.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.acrylic.outbound.pda.ui.SubmissionActivity
import org.junit.Assert.*

object SubmissionScreenScenario {
    fun freeze(app: PdaApplication, assignment: String): String {
        ActivityScenario.launch<SubmissionActivity>(Intent(app, SubmissionActivity::class.java).putExtra("assignmentId", assignment)).use { screen ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            var ready = false
            waitUntil { screen.onActivity { ready = it.findViewById<Button>(R.id.freeze).isEnabled }; ready && app.work.state.value.queued == 0 }
            screen.onActivity { activity ->
                activity.findViewById<EditText>(R.id.reason).setText("实物核对后申请短装")
                assertTrue(activity.findViewById<Button>(R.id.freeze).performClick())
            }
            onView(withText("确认冻结")).perform(click())
            waitUntil { app.database.submissions().forOrder(assignment) != null }
            screen.recreate()
        }
        return checkNotNull(app.database.submissions().forOrder(assignment)).id
    }
    fun showsSuccess(app: PdaApplication, assignment: String) {
        ActivityScenario.launch<SubmissionActivity>(Intent(app, SubmissionActivity::class.java).putExtra("assignmentId", assignment)).use { screen ->
            var displayed = false
            waitUntil {
                screen.onActivity { displayed = it.findViewById<TextView>(R.id.status).text.contains("云端已确认出库") }
                displayed
            }
        }
        ActivityScenario.launch<com.acrylic.outbound.pda.ui.WorkActivity>(
            Intent(app, com.acrylic.outbound.pda.ui.WorkActivity::class.java).putExtra("assignmentId", assignment)
        ).use { screen ->
            var frozen = false
            waitUntil {
                screen.onActivity { frozen = it.findViewById<android.view.View>(R.id.workControls).visibility == android.view.View.GONE }
                frozen
            }
        }
    }
    private fun waitUntil(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 10000
        while (!condition() && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(25)
        assertTrue("Expected submission state did not arrive", condition())
    }
}
