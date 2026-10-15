package com.acrylic.outbound.pda

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.widget.ScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.ui.TemporaryPreparationActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Observes the original preparation screen through real swipes without requesting new data. */
class TemporaryPreparationViewportDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private lateinit var directory: File
    private val touches = JSONArray()

    @Test fun userScrollReachesNextPreparationWithoutChangingData() {
        val args = InstrumentationRegistry.getArguments()
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(args.getString("preparationViewport") == "true")
        val run = requireNotNull(args.getString("evidenceRun"))
        require(run.matches(Regex("[a-zA-Z0-9-]{1,50}")))
        directory = File(app.filesDir, "preparation-viewport/$run")
        check(directory.mkdirs())
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        val before = DraftDiagnosticDatabaseSnapshot(app).read().toString(2)
        File(directory, "database-before.json").writeText(before)
        try { inspectScreen() } finally {
            File(directory, "touch-events.json").writeText(touches.toString(2))
            val after = DraftDiagnosticDatabaseSnapshot(app).read().toString(2)
            File(directory, "database-after.json").writeText(after)
            assertEquals(before, after)
        }
    }

    private fun inspectScreen() {
        ActivityScenario.launch<TemporaryPreparationActivity>(Intent(app, TemporaryPreparationActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.nextPreparation).setOnTouchListener { view, event ->
                    touches.put(JSONObject().put("action", event.actionMasked).put("x", event.x).put("y", event.y)
                        .put("focused", view.hasFocus()).put("pressed", view.isPressed))
                    false
                }
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            record("before")
            onView(isAssignableFrom(ScrollView::class.java)).perform(swipeUp(), swipeUp())
            record("after-swipes")
            onView(withId(R.id.nextPreparation)).check(matches(isCompletelyDisplayed()))
            onView(withId(R.id.nextPreparation)).perform(click())
            scenario.onActivity { capture(it.window.decorView, "after-click") }
            onView(withText("联网准备新资料")).check(matches(isDisplayed()))
            onView(withId(android.R.id.button2)).perform(click())
        }
    }

    private fun record(name: String) {
        onView(isRoot()).perform(object : androidx.test.espresso.ViewAction {
            override fun getConstraints() = isRoot()
            override fun getDescription() = "Capture actual preparation viewport without changing it"
            override fun perform(controller: androidx.test.espresso.UiController, view: View) {
                capture(view, name)
            }
        })
    }

    private fun capture(view: View, name: String) {
        val target = view.findViewById<View>(R.id.nextPreparation)
        val scroll = generateSequence(target.parent as? View) { it.parent as? View }.filterIsInstance<ScrollView>().first()
        val facts = JSONObject().put("root", dimensions(view)).put("nextPreparation", dimensions(target))
            .put("scroll", dimensions(scroll)).put("scrollY", scroll.scrollY)
            .put("scrollContentHeight", scroll.getChildAt(0).height)
        File(directory, "$name.json").writeText(facts.toString(2))
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun dimensions(view: View): JSONObject {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val visible = Rect()
        view.getGlobalVisibleRect(visible)
        return JSONObject().put("x", location[0]).put("y", location[1]).put("width", view.width).put("height", view.height)
            .put("visible", visible.toShortString()).put("paddingBottom", view.paddingBottom).put("paddingTop", view.paddingTop)
    }
}
