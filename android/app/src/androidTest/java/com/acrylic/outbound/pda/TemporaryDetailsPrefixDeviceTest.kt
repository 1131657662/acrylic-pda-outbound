package com.acrylic.outbound.pda

import android.content.Intent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.security.SessionVault
import com.acrylic.outbound.pda.ui.TemporaryDetailsActivity
import org.hamcrest.Matchers.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Direct product entry for a synthetic blocked R06 predecessor; no activation or new scan is performed. */
class TemporaryDetailsPrefixDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication

    @Test fun blockedPredecessorRemainsReadOnlyThroughFilterLocateAndIdentityChange() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("temporaryDetails") == "synthetic-1704")
        val run = checkNotNull(args.getString("evidenceRun")); check(run.matches(Regex("[a-z0-9-]{1,40}")))
        val directory = File(app.filesDir, "temporary-details-prefix-input")
        val identity = JSONObject(File(directory, "identity.json").readText()); check(identity.getBoolean("syntheticOnly"))
        val session = JSONObject(File(directory, "synthetic-session.json").readText())
        assertEquals(identity.getString("userId"), session.getString("userId"))
        assertEquals(identity.getString("deviceId"), session.getString("deviceId"))
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        SessionVault(app).save(session)
        val ui = TemporaryDetailsDeviceUi(File(app.filesDir, "temporary-details-evidence/$run"))
        ActivityScenario.launch<TemporaryDetailsActivity>(Intent(app, TemporaryDetailsActivity::class.java)
            .putExtra("clientOrderId", identity.getString("clientOrderId"))).use { scenario ->
            inspect(scenario, ui)
            ui.click(R.id.filter); onData(equalTo("主管保留")).perform(click())
            inspect(scenario, ui)
            ui.click(R.id.locateMode); onData(equalTo("完整条码")).perform(click())
            ui.reveal(R.id.locateInput); onView(withId(R.id.locateInput)).perform(replaceText(identity.getString("barcode")), androidx.test.espresso.action.ViewActions.closeSoftKeyboard())
            ui.click(R.id.locate); inspect(scenario, ui); ui.inspect("blocked-prefix")
            changedIdentity(scenario, ui, session)
            ui.click(R.id.back)
        }
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        File(app.filesDir, "temporary-details-evidence/$run/completed.txt").writeText("Synthetic blocked R06 prefix, read/filter/locate only.\n")
    }

    private fun inspect(scenario: ActivityScenario<TemporaryDetailsActivity>, ui: TemporaryDetailsDeviceUi) {
        ui.await {
            onView(withId(R.id.retry)).check(matches(isEnabled()))
            onView(withId(R.id.adjustPackages)).check(matches(not(isEnabled())))
            onView(withId(R.id.previous)).check(matches(not(isEnabled())))
            onView(withId(R.id.next)).check(matches(not(isEnabled())))
            onView(withId(R.id.summary)).check(matches(withText(containsString("不授予新作业或实物放行资格"))))
            scenario.onActivity {
                val rows = it.findViewById<LinearLayout>(R.id.rows)
                assertEquals(1, rows.childCount)
                assertTrue((rows.getChildAt(0) as TextView).text.contains("主管核验保留，须受控处置"))
            }
        }
    }

    private fun changedIdentity(scenario: ActivityScenario<TemporaryDetailsActivity>, ui: TemporaryDetailsDeviceUi, session: JSONObject) {
        SessionVault(app).save(JSONObject(session.toString()).put("userId", "synthetic-wrong-user"))
        ui.click(R.id.retry)
        ui.await {
            onView(withId(R.id.status)).check(matches(withText(containsString("原临时准备属于旧身份或版本"))))
            scenario.onActivity { assertEquals(0, it.findViewById<LinearLayout>(R.id.rows).childCount) }
        }
        SessionVault(app).save(session); ui.click(R.id.first); inspect(scenario, ui)
    }
}
