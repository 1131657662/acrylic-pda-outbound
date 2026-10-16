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

/** Explicit synthetic input on a newly allocated emulator, never the retained original AVD. */
class TemporaryDetailsDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val args get() = InstrumentationRegistry.getArguments()
    private lateinit var identity: JSONObject
    private lateinit var ui: TemporaryDetailsDeviceUi
    private lateinit var scenario: ActivityScenario<TemporaryDetailsActivity>

    @Test fun originalPagesFiltersLocatorAndLargeTextRemainUsable() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(args.getString("temporaryDetails") == "synthetic-1703")
        val run = checkNotNull(args.getString("evidenceRun")); check(run.matches(Regex("[a-z0-9-]{1,40}")))
        val source = File(app.filesDir, "temporary-details-input")
        identity = JSONObject(File(source, "identity.json").readText()); check(identity.getBoolean("syntheticOnly"))
        val session = JSONObject(File(source, "synthetic-session.json").readText())
        assertEquals(identity.getString("userId"), session.getString("userId"))
        assertEquals(identity.getString("deviceId"), session.getString("deviceId"))
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        SessionVault(app).save(session)
        ui = TemporaryDetailsDeviceUi(File(app.filesDir, "temporary-details-evidence/$run"))
        ActivityScenario.launch<TemporaryDetailsActivity>(Intent(app, TemporaryDetailsActivity::class.java)
            .putExtra("clientOrderId", identity.getString("clientOrderId"))).use {
            scenario = it
            qualification(); paging(); filters(); locator(); identityChange(session); auxiliaryEntries()
            ui.click(R.id.back)
        }
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        File(app.filesDir, "temporary-details-evidence/$run/completed.txt").writeText("Synthetic TEMP product-page interaction only; no save/adjust/export command.\n")
    }

    private fun qualification() {
        expectRows(1, 20)
        onView(withId(R.id.adjustPackages)).check(matches(withText(containsString("本机作业资格资料不完整"))))
        ui.click(R.id.qualificationDetails)
        onView(withId(android.R.id.message)).check(matches(withText("No value for timeAnchor")))
        ui.inspectDialog("qualification")
        onView(withId(android.R.id.button1)).perform(click())
    }

    private fun paging() {
        expectRows(1, 20); ui.inspect("first")
        ui.click(R.id.next); expectRows(21, 20)
        ui.click(R.id.next); expectRows(41, 10)
        onView(withId(R.id.next)).check(matches(not(isEnabled())))
        ui.inspect("last")
        ui.click(R.id.previous); expectRows(21, 20)
        scenario.recreate(); expectRows(21, 20)
        ui.click(R.id.previous); expectRows(1, 20)
        onView(withId(R.id.previous)).check(matches(not(isEnabled())))
    }

    private fun filters() {
        select(R.id.filter, "已撤销"); expectRows(1, 20)
        assertRowsContain("已撤销 · 保留原记录")
        select(R.id.filter, "未处理输入"); expectRows(47, 4)
        assertRowsContain("尚未保存", false)
        ui.inspect("pending")
        select(R.id.filter, "已保存原记录"); expectRows(46, 1)
        assertRowsContain("已保存")
        select(R.id.filter, "全部原记录"); expectRows(1, 20)
    }

    private fun locator() {
        enter("5"); ui.click(R.id.locate); expectRows(5, 20)
        select(R.id.locateMode, "完整条码"); enter(identity.getString("lastSavedBarcode"))
        ui.click(R.id.locate); expectRows(46, 5)
        scenario.recreate(); expectRows(46, 5)
        enter(identity.getString("firstBarcode")); ui.click(R.id.locate); expectRows(50, 1)
        enter("99999999999"); ui.click(R.id.locate)
        ui.await { onView(withId(R.id.status)).check(matches(withText(containsString("没有匹配原记录")))) }
        ui.click(R.id.first); expectRows(1, 20)
        select(R.id.locateMode, "原序号"); enter("50")
        ui.reveal(R.id.locateInput); onView(withId(R.id.locateInput)).perform(click())
        ui.await { onView(isRoot()).check { view, _ -> assertTrue(androidx.core.view.ViewCompat.getRootWindowInsets(view)
            ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true) } }
        ui.reveal(R.id.locate); ui.inspect("keyboard"); androidx.test.espresso.Espresso.closeSoftKeyboard()
        ui.click(R.id.locate); expectRows(50, 1)
    }

    private fun identityChange(session: JSONObject) {
        SessionVault(app).save(JSONObject(session.toString()).put("userId", "synthetic-wrong-user"))
        ui.click(R.id.retry)
        ui.await { onView(withId(R.id.status)).check(matches(withText(containsString("原临时准备属于旧身份或版本")))) }
        scenario.onActivity { assertEquals(0, it.findViewById<LinearLayout>(R.id.rows).childCount) }
        SessionVault(app).save(session); ui.click(R.id.first); expectRows(1, 20)
    }

    private fun auxiliaryEntries() {
        ui.click(R.id.cancelDraft)
        ui.await { onView(withId(R.id.status)).check(matches(withText(containsString("不符合普通取消条件")))) }
        onView(withId(R.id.cancelDraft)).check(matches(not(isEnabled())))
        ui.click(R.id.back); expectRows(1, 20)
        ui.click(R.id.rescueExport)
        ui.reveal(R.id.credential)
        onView(withId(R.id.credential)).check(matches(isDisplayed()))
        ui.click(R.id.back); expectRows(1, 20)
    }

    private fun enter(value: String) {
        ui.reveal(R.id.locateInput); onView(withId(R.id.locateInput)).perform(replaceText(value)); androidx.test.espresso.Espresso.closeSoftKeyboard()
    }
    private fun select(id: Int, label: String) {
        ui.click(id); onData(equalTo(label)).perform(click())
    }
    private fun expectRows(first: Long, count: Int) {
        ui.await {
            onView(withId(R.id.retry)).check(matches(isEnabled()))
            scenario.onActivity {
                val rows = it.findViewById<LinearLayout>(R.id.rows)
                assertEquals(count, rows.childCount)
                assertTrue((rows.getChildAt(0) as TextView).text.startsWith("原序号 $first ·"))
            }
        }
    }
    private fun assertRowsContain(text: String, every: Boolean = true) {
        scenario.onActivity {
            val rows = it.findViewById<LinearLayout>(R.id.rows)
            val checks = (0 until rows.childCount).map { index -> (rows.getChildAt(index) as TextView).text.contains(text) }
            assertTrue(if (every) checks.all { match -> match } else checks.any { match -> match })
        }
    }
}
