package com.acrylic.outbound.pda

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.ui.TemporaryDraftActivity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Existing isolated draft; edit the page only, leaving every durable row unchanged. */
class TemporaryDraftEditorDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as PdaApplication
    private lateinit var directory: File

    @Test fun pageEditsSurviveRecreationUntilExplicitDiscard() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("draftEditorVerification") == "true")
        val run = requireNotNull(args.getString("evidenceRun"))
        require(run.matches(Regex("[a-zA-Z0-9-]{1,50}")))
        directory = File(app.filesDir, "draft-editor-verification/$run")
        check(directory.mkdirs()) { "Existing evidence must be preserved" }
        val id = requireNotNull(args.getString("draftId"))
        require(java.util.UUID.fromString(id).toString() == id)
        val original = requireNotNull(app.database.temporaryDrafts().get(id))
        check(original.state == "DRAFT" && app.database.temporaryDrafts().lines(id).isEmpty())
        val before = DraftDiagnosticDatabaseSnapshot(app).read().toString(2)
        File(directory, "database-before.json").writeText(before)
        try { editOriginal(id, original.vehicleNo) } finally {
            val after = DraftDiagnosticDatabaseSnapshot(app).read().toString(2)
            File(directory, "database-after.json").writeText(after)
            assertEquals(original, app.database.temporaryDrafts().get(id))
            assertEquals(before, after)
        }
    }

    private fun editOriginal(id: String, originalVehicle: String) {
        val intent = Intent(app, TemporaryDraftActivity::class.java).putExtra("clientOrderId", id)
        ActivityScenario.launch<TemporaryDraftActivity>(intent).use { scenario ->
            try {
                awaitReady()
                onView(withId(R.id.vehicle)).perform(scrollTo(), replaceText("隔离页面编辑验证"), closeSoftKeyboard())
                invalidInputDoesNotAdd(scenario)
                addTwoBags()
                editQuantity(scenario)
                removeWithConfirmation(scenario)
                addTwoBags()
                scenario.recreate()
                awaitReady()
                assertPage(scenario, "after-recreation", 1 to "2 包")
                discardPage()
                awaitExit(scenario)
            } finally {
                if (scenario.state != androidx.lifecycle.Lifecycle.State.DESTROYED)
                    scenario.onActivity { capture(it, "final-page") }
            }
        }
        ActivityScenario.launch<TemporaryDraftActivity>(intent).use { scenario ->
            awaitReady()
            assertPage(scenario, "reopened-original", 0 to "")
            onView(withId(R.id.vehicle)).check(matches(withText(originalVehicle)))
            onView(withId(R.id.dirty)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }

    private fun invalidInputDoesNotAdd(scenario: ActivityScenario<TemporaryDraftActivity>) {
        onView(withId(R.id.productSearch)).perform(scrollTo(), replaceText("NO-MATCH-DIAGNOSTIC"), pressImeActionButton())
        onView(withId(R.id.count)).perform(scrollTo(), replaceText("1"), pressImeActionButton())
        onView(withId(R.id.add)).perform(scrollTo(), click())
        onView(withId(R.id.productSearch)).check(matches(hasErrorText("请选择匹配产品后添加")))
        assertPage(scenario, "unmatched-rejected", 0 to "")
        onView(withId(R.id.productSearch)).perform(scrollTo(), replaceText("003"), pressImeActionButton())
        onView(withId(R.id.count)).perform(scrollTo(), replaceText("0"), pressImeActionButton())
        onView(withId(R.id.add)).perform(scrollTo(), click())
        onView(withId(R.id.count)).check(matches(hasErrorText("请输入正整数计划包数")))
        assertPage(scenario, "zero-rejected", 0 to "")
    }

    private fun addTwoBags() {
        onView(withId(R.id.productSearch)).perform(scrollTo(), replaceText("003"), pressImeActionButton())
        onView(withId(R.id.count)).perform(scrollTo(), replaceText("2"), pressImeActionButton())
        onView(withId(R.id.add)).perform(scrollTo(), click())
        onView(withId(R.id.count)).check(matches(withText("")))
        listOf(R.id.productSearch, R.id.count).forEach { id ->
            onView(withId(id)).check { view, _ -> assertNull((view as EditText).error) }
        }
        onView(withId(R.id.dirty)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
    }

    private fun editQuantity(scenario: ActivityScenario<TemporaryDraftActivity>) {
        onView(withText("修改 003 数量")).perform(scrollTo(), click())
        onView(isAssignableFrom(EditText::class.java)).inRoot(isDialog()).perform(replaceText("0"), closeSoftKeyboard())
        onView(withText("保存页面修改")).inRoot(isDialog()).perform(click())
        onView(isAssignableFrom(EditText::class.java)).inRoot(isDialog()).check(matches(hasErrorText("请输入正整数计划包数")))
        onView(isAssignableFrom(EditText::class.java)).inRoot(isDialog()).perform(replaceText("3"), closeSoftKeyboard())
        onView(withText("保存页面修改")).inRoot(isDialog()).perform(click())
        assertPage(scenario, "quantity-updated", 1 to "3 包")
    }

    private fun removeWithConfirmation(scenario: ActivityScenario<TemporaryDraftActivity>) {
        onView(withText("移除产品 003")).perform(scrollTo(), click())
        onView(withText("保留")).inRoot(isDialog()).perform(click())
        assertPage(scenario, "removal-cancelled", 1 to "3 包")
        onView(withText("移除产品 003")).perform(scrollTo(), click())
        onView(withText("确认移除")).inRoot(isDialog()).perform(click())
        assertPage(scenario, "removal-confirmed", 0 to "")
    }

    private fun discardPage() {
        pressBack()
        onView(withText("继续编辑")).inRoot(isDialog()).perform(click())
        onView(withId(R.id.dirty)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
        pressBack()
        onView(withText("保留已存草稿并返回")).inRoot(isDialog()).perform(click())
    }

    private fun assertPage(scenario: ActivityScenario<TemporaryDraftActivity>, name: String, expected: Pair<Int, String>) {
        scenario.onActivity { activity ->
            val lines = activity.findViewById<LinearLayout>(R.id.lines)
            assertEquals(expected.first, lines.childCount)
            if (expected.first > 0) assertTrue(((lines.getChildAt(0) as LinearLayout).getChildAt(0) as TextView).text.contains(expected.second))
            capture(activity, name)
        }
    }

    private fun capture(activity: TemporaryDraftActivity, name: String) {
        val lines = activity.findViewById<LinearLayout>(R.id.lines)
        File(directory, "$name.json").writeText(JSONObject().put("lines", lines.childCount)
            .put("dirtyVisible", activity.findViewById<android.view.View>(R.id.dirty).visibility == android.view.View.VISIBLE).toString(2))
        val root = activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun awaitReady() {
        val end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30)
        var last: AssertionError? = null
        do {
            try { onView(withId(R.id.add)).check(matches(isEnabled())); return } catch (error: AssertionError) { last = error }
            Thread.sleep(100)
        } while (System.nanoTime() < end)
        throw AssertionError("Existing draft did not become ready", last)
    }

    private fun awaitExit(scenario: ActivityScenario<TemporaryDraftActivity>) {
        val end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
        while (scenario.state != androidx.lifecycle.Lifecycle.State.DESTROYED && System.nanoTime() < end) {
            instrumentation.waitForIdleSync()
            Thread.sleep(50)
        }
        assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, scenario.state)
    }
}
