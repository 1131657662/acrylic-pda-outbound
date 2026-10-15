package com.acrylic.outbound.pda

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.widget.*
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.ui.TemporaryDraftActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Diagnostic only: an existing isolated draft, no plan save, scan, identity or clock replacement. */
class TemporaryDraftTouchDiagnosticDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as PdaApplication
    private val directory by lazy {
        val run = requireNotNull(InstrumentationRegistry.getArguments().getString("evidenceRun"))
        require(run.matches(Regex("[a-zA-Z0-9-]{1,50}")))
        File(app.filesDir, "draft-touch-diagnostic01/$run")
    }
    private val touches = JSONArray()

    @Test fun recordRealTouchThenOriginalClickListenerWithoutSavingDraft() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("draftTouchDiagnostic") == "true")
        check(directory.mkdirs()) { "Use a new evidenceRun; existing device evidence is preserved" }
        val id = requireNotNull(InstrumentationRegistry.getArguments().getString("draftId"))
        require(java.util.UUID.fromString(id).toString() == id)
        val original = requireNotNull(app.database.temporaryDrafts().get(id))
        check(original.state == "DRAFT")
        val before = DraftDiagnosticDatabaseSnapshot(app).read()
        File(directory, "database-before.json").writeText(before.toString(2))
        val intent = Intent(app, TemporaryDraftActivity::class.java).putExtra("clientOrderId", id)
        try {
            ActivityScenario.launch<TemporaryDraftActivity>(intent).use { scenario ->
                try { diagnose(scenario) } finally { scenario.onActivity { capture(it, "final-page") } }
            }
        } finally {
            File(directory, "touch-events.json").writeText(touches.toString(2))
            val after = DraftDiagnosticDatabaseSnapshot(app).read()
            File(directory, "database-after.json").writeText(after.toString(2))
            assertEquals(original, app.database.temporaryDrafts().get(id))
            assertEquals(before.toString(), after.toString())
        }
    }

    private fun diagnose(scenario: ActivityScenario<TemporaryDraftActivity>) {
        val mode = InstrumentationRegistry.getArguments().getString("scrollPreparation")
        observeButtonTouch(scenario)
        if (mode == "original-sequence") prepareOriginalInputs(scenario) else prepareInputs(scenario)
        prepareScroll(scenario, mode)
        scenario.onActivity { capture(it, "before-touch") }
        try {
            if (mode in setOf("espresso", "espresso-move", "original-sequence"))
                onView(withId(R.id.add)).perform(scrollTo(), click())
            else onView(withId(R.id.add)).perform(click())
        } finally {
            instrumentation.waitForIdleSync()
            scenario.onActivity { capture(it, "after-touch") }
        }
        scenario.onActivity { activity ->
            if (activity.findViewById<LinearLayout>(R.id.lines).childCount == 0) {
                val result = activity.findViewById<Button>(R.id.add).performClick()
                File(directory, "perform-click.json").writeText(JSONObject().put("returned", result).toString())
                capture(activity, "after-perform-click")
            }
        }
    }

    private fun observeButtonTouch(scenario: ActivityScenario<TemporaryDraftActivity>) {
        scenario.onActivity { activity ->
            val button = activity.findViewById<Button>(R.id.add)
            button.setOnTouchListener { view, event ->
                touches.put(JSONObject().put("action", event.actionMasked).put("eventTime", event.eventTime)
                    .put("x", event.x).put("y", event.y).put("pressed", view.isPressed).put("focused", view.hasFocus()))
                false
            }
        }
    }

    private fun prepareScroll(scenario: ActivityScenario<TemporaryDraftActivity>, mode: String?) {
        if (mode == "espresso-move" || mode == "gesture-move") scenario.onActivity { activity ->
            val scroll = activity.findViewById<ScrollView>(R.id.draftScroll)
            scroll.scrollTo(0, 0)
        }
        if (mode == "gesture-move") {
            repeat(8) { step ->
                var visible = false
                scenario.onActivity { activity ->
                    val view = activity.findViewById<View>(R.id.add)
                    val rect = Rect()
                    visible = view.getGlobalVisibleRect(rect) && rect.height() == view.height
                }
                if (visible) return
                onView(isRoot()).perform(swipeUp())
                scenario.onActivity { capture(it, "gesture-$step") }
            }
            onView(withId(R.id.add)).check(matches(isCompletelyDisplayed()))
        }
        if (mode !in setOf("espresso", "espresso-move", "gesture-move", "original-sequence"))
            revealForDiagnostic(scenario, R.id.add)
    }

    /** Original failing consumer's input sequence, stopped before any plan save or scan. */
    private fun prepareOriginalInputs(scenario: ActivityScenario<TemporaryDraftActivity>) {
        await { onView(withId(R.id.add)).check(matches(isEnabled())) }
        scenario.onActivity { capture(it, "initial") }
        onView(withId(R.id.vehicle)).perform(scrollTo(), replaceText("高频隔离车"), closeSoftKeyboard())
        onView(withId(R.id.reason)).perform(scrollTo(), replaceText("合成离线高频验证"), closeSoftKeyboard())
        onView(withId(R.id.productSearch)).perform(scrollTo(), replaceText("003"), pressImeActionButton())
        scenario.onActivity { capture(it, "search-entered") }
        onView(isRoot()).perform(swipeUp())
        onView(withId(R.id.count)).check(matches(isDisplayed()))
        onView(withId(R.id.count)).perform(replaceText("1"), pressImeActionButton())
    }

    private fun prepareInputs(scenario: ActivityScenario<TemporaryDraftActivity>) {
        await { onView(withId(R.id.add)).check(matches(isEnabled())) }
        scenario.onActivity { capture(it, "initial") }
        revealForDiagnostic(scenario, R.id.productSearch)
        onView(withId(R.id.productSearch)).perform(replaceText("003"), pressImeActionButton())
        revealForDiagnostic(scenario, R.id.count)
        onView(withId(R.id.count)).perform(replaceText("1"), pressImeActionButton())
    }

    /** Position preparation only, deliberately not a scrolling usability acceptance. */
    private fun revealForDiagnostic(scenario: ActivityScenario<TemporaryDraftActivity>, id: Int) {
        scenario.onActivity { activity ->
            val view = activity.findViewById<View>(id)
            val scroll = activity.findViewById<ScrollView>(R.id.draftScroll)
            scroll.scrollTo(0, (view.top - scroll.height / 3).coerceAtLeast(0))
        }
        instrumentation.waitForIdleSync()
        onView(withId(id)).check(matches(isCompletelyDisplayed()))
    }

    private fun capture(activity: TemporaryDraftActivity, name: String) {
        val root = activity.window.decorView
        val scroll = activity.findViewById<ScrollView>(R.id.draftScroll)
        val result = JSONObject().put("focus", activity.currentFocus?.id).put("touches", touches.length())
            .put("scrollY", scroll.scrollY).put("layoutRequested", root.isLayoutRequested)
            .put("viewport", describe(scroll))
        listOf(R.id.productSearch, R.id.product, R.id.count, R.id.add, R.id.lines, R.id.status).forEach { id ->
            val view = activity.findViewById<View>(id)
            result.put(activity.resources.getResourceEntryName(id), describe(view))
        }
        File(directory, "$name.json").writeText(result.toString(2))
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun describe(view: View): JSONObject {
        val position = IntArray(2).also(view::getLocationOnScreen)
        val visible = Rect()
        return JSONObject().put("x", position[0]).put("y", position[1]).put("width", view.width).put("height", view.height)
            .put("globalVisible", view.getGlobalVisibleRect(visible)).put("visibleRect", visible.toShortString())
            .put("enabled", view.isEnabled).put("clickable", view.isClickable).put("hasClickListener", view.hasOnClickListeners())
            .put("focused", view.hasFocus()).put("focusableInTouchMode", view.isFocusableInTouchMode)
            .put("text", (view as? TextView)?.text).put("error", (view as? TextView)?.error)
            .put("selectedPosition", (view as? Spinner)?.selectedItemPosition).put("selectedItem", (view as? Spinner)?.selectedItem)
            .put("childCount", (view as? LinearLayout)?.childCount)
    }

    private fun await(check: () -> Unit) {
        val end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30)
        var last: Throwable? = null
        do {
            try { check(); return } catch (error: AssertionError) { last = error }
            Thread.sleep(100)
        } while (System.nanoTime() < end)
        throw AssertionError("Draft diagnostic input did not become available", last)
    }
}
