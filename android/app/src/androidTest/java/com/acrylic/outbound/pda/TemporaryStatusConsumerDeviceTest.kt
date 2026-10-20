package com.acrylic.outbound.pda

import android.content.Intent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
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
import java.security.MessageDigest

/** Native read-only consumer of preserved synthetic TEMP originals; never creates or activates a task. */
class TemporaryStatusConsumerDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private lateinit var scenario: ActivityScenario<TemporaryDetailsActivity>
    private lateinit var ui: TemporaryDetailsDeviceUi
    private lateinit var evidence: File
    private lateinit var identity: JSONObject
    private var summary = ""
    private var products = ""

    @Test fun approvedTemporaryStatusChoicesOnlyChangeVisibleOriginalRows() {
        guard(); val session = app.session.requireSession().toString()
        val before = DraftDiagnosticDatabaseSnapshot(app).read().toString(2)
        File(evidence, "database-before.json").writeText(before)
        try {
            ActivityScenario.launch<TemporaryDetailsActivity>(Intent(app, TemporaryDetailsActivity::class.java)
                .putExtra("clientOrderId", identity.getString("clientOrderId"))).use {
                scenario = it; paging(); filters(); locator(); ui.inspect("temporary-final"); ui.click(R.id.back)
            }
            assertEquals(before, DraftDiagnosticDatabaseSnapshot(app).read().toString(2))
            assertEquals(session, app.session.requireSession().toString())
            File(evidence, "result.json").writeText(JSONObject().put("roomUnchanged", true).put("sessionUnchanged", true)
                .put("actualProductSpinner", true).put("retainedPositiveInputAvailable", false)
                .put("fontScale", app.resources.configuration.fontScale).put("localDeletionAllowed", false).toString(2))
        } finally { File(evidence, "database-after.json").writeText(DraftDiagnosticDatabaseSnapshot(app).read().toString(2)) }
    }

    private fun guard() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(!BuildConfig.TEMPORARY_CONTINUATION_DEVELOPMENT && !BuildConfig.TEMPORARY_HANDOVER_DEVELOPMENT)
        check(!BuildConfig.RECOVERED_HANDOVER_DEVELOPMENT)
        val args = InstrumentationRegistry.getArguments(); check(args.getString("temporaryStatusConsumer") == "retained-synthetic-only")
        val run = checkNotNull(args.getString("evidenceRun")); check(run.matches(Regex("[a-z0-9-]{1,40}")))
        val root = File(app.filesDir, "temporary-status-input")
        val input = File(root, "synthetic-session.json"); check(sha(input.readBytes()) == args.getString("sessionSha256"))
        identity = JSONObject(File(root, "identity.json").readText()); check(identity.getBoolean("syntheticOnly"))
        val session = JSONObject(input.readText())
        assertEquals(identity.getString("userId"), session.getString("userId"))
        assertEquals(identity.getString("deviceId"), session.getString("deviceId"))
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get(); app.work.pause(); app.temporaryWork.pause()
        if (app.session.saved() == null) SessionVault(app).save(session)
        assertEquals(session.getString("userId"), app.session.requireSession().getString("userId"))
        evidence = File(app.filesDir, "temporary-status-evidence/$run").also { check(it.mkdirs()) }
        ui = TemporaryDetailsDeviceUi(evidence)
        assertEquals(checkNotNull(args.getString("expectedFontScale")).toFloat(), app.resources.configuration.fontScale, .01f)
    }

    private fun paging() {
        rows((1L..20L).toList()); onView(withId(R.id.filter)).check(matches(withSpinnerText("全部")))
        scenario.onActivity { summary = it.findViewById<TextView>(R.id.summary).text.toString()
            products = it.findViewById<TextView>(R.id.products).text.toString() }
        ui.inspect("default-all")
        ui.click(R.id.next); rows((21L..40L).toList()); ui.click(R.id.next); rows((41L..50L).toList())
        onView(withId(R.id.next)).check(matches(not(isEnabled())))
        ui.click(R.id.previous); rows((21L..40L).toList()); scenario.recreate(); rows((21L..40L).toList())
        ui.click(R.id.first); rows((1L..20L).toList())
    }

    private fun filters() {
        select("有效"); rows(listOf(46L)); unchangedSummary(); ui.inspect("active")
        select("已撤销"); rows((1L..20L).toList()); ui.click(R.id.next); rows((21L..40L).toList())
        ui.click(R.id.next); rows((41L..45L).toList()); scenario.recreate(); rows((41L..45L).toList())
        onView(withId(R.id.filter)).check(matches(withSpinnerText("已撤销"))); unchangedSummary()
        select("主管保留"); rows(emptyList()); unchangedSummary(); ui.inspect("retained-empty")
        onView(withId(R.id.status)).check(matches(withText(containsString("暂无匹配记录"))))
    }

    private fun locator() {
        ui.click(R.id.locateMode); onData(equalTo("完整条码")).perform(click())
        ui.reveal(R.id.locateInput)
        onView(withId(R.id.locateInput)).perform(replaceText(identity.getString("lastSavedBarcode")), closeSoftKeyboard())
        ui.click(R.id.locate); rows((46L..50L).toList())
        onView(withId(R.id.filter)).check(matches(withSpinnerText("全部")))
        unchangedSummary(); scenario.recreate(); rows((46L..50L).toList())
    }

    private fun select(label: String) { ui.click(R.id.filter); onData(equalTo(label)).perform(click()) }

    private fun rows(expected: List<Long>) {
        ui.await {
            onView(withId(R.id.retry)).check(matches(isEnabled()))
            scenario.onActivity { activity ->
                val rows = activity.findViewById<LinearLayout>(R.id.rows)
                val actual = (0 until rows.childCount).map { index -> (rows.getChildAt(index) as TextView).text.toString()
                    .substringAfter("原序号 ").substringBefore(" ·").toLong() }
                assertEquals(expected, actual)
            }
        }
    }

    private fun unchangedSummary() {
        scenario.onActivity { assertEquals(summary, it.findViewById<TextView>(R.id.summary).text.toString())
            assertEquals(products, it.findViewById<TextView>(R.id.products).text.toString()) }
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
