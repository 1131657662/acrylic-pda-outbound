package com.acrylic.outbound.pda

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.ui.TemporarySubmissionActivity
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** UI actions only: real HTTP acceptance is independently covered by the preserved source run. */
class TemporaryStopDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication

    @Test fun stopRequiresReasonThenPersistsVerifiedReceiptAndReturnsSafely() {
        TemporaryStopUiFixture(app).use { f ->
            f.prepare()
            val before = checkNotNull(app.database.temporarySubmissions().forOrder(f.id))
            ActivityScenario.launch<TemporarySubmissionActivity>(Intent(app, TemporarySubmissionActivity::class.java)
                .putExtra("clientOrderId", f.id)).use { scenario ->
                await { onView(withId(R.id.status)).check(matches(withText(containsString("待独立确认")))) }
                openStop(scenario, f)
                onView(withText("确认停止")).perform(click())
                await { onView(withId(R.id.error)).check(matches(withText(containsString("停止须填写")))) }
                assertNull(app.database.temporarySubmissions().get(before.id)!!.abortReason)
                openStop(scenario, f)
                onView(withHint("停止原因")).perform(replaceText(f.input.getJSONObject("request").getString("reason")), closeSoftKeyboard())
                onView(withText("确认停止")).perform(click())
                await { onView(withId(R.id.status)).check(matches(withText(containsString("停止回执已验签")))) }
                onView(withId(R.id.status)).perform(scrollTo())
                scenario.onActivity { screen(it, f) }
                val stopped = checkNotNull(app.database.temporarySubmissions().get(before.id))
                assertEquals("ABORTED", stopped.state); assertEquals(before.payload, stopped.payload)
                assertEquals(f.input.getJSONObject("request").getString("reason"), stopped.abortReason)
                assertEquals(1, f.stops)
                onView(withId(R.id.back)).perform(scrollTo(), click())
            }
            File(f.directory, "completed.txt").writeText("Actual emulator reason validation, signed stop, immutable payload and return. Synthetic identity and network replay.")
        }
    }

    private fun openStop(scenario: ActivityScenario<TemporarySubmissionActivity>, f: TemporaryStopUiFixture) {
        onView(isAssignableFrom(android.widget.ScrollView::class.java)).perform(swipeUp())
        try { onView(withText("停止本轮续作 · 保留实物占用")).check(matches(isCompletelyDisplayed())).perform(click()) }
        catch (error: Throwable) { scenario.onActivity { screen(it, f) }; throw error }
    }

    private fun screen(activity: TemporarySubmissionActivity, f: TemporaryStopUiFixture) {
        val root = activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888); root.draw(Canvas(bitmap))
        File(f.directory, "stopped.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        File(f.directory, "status.txt").writeText(activity.findViewById<TextView>(R.id.status).text.toString())
        val rows = mutableListOf<String>(); describe(root, rows)
        File(f.directory, "views.txt").writeText(rows.joinToString("\n"))
    }
    private fun describe(view: android.view.View, rows: MutableList<String>) {
        val visible = android.graphics.Rect(); view.getGlobalVisibleRect(visible)
        rows.add("${view.javaClass.simpleName}: ${view.width}x${view.height} y=${view.top} scroll=${view.scrollY} visible=$visible text=${(view as? TextView)?.text}")
        if (view is android.view.ViewGroup) for (i in 0 until view.childCount) describe(view.getChildAt(i), rows)
    }
    private fun await(check: () -> Unit) {
        val end = android.os.SystemClock.elapsedRealtime() + 20_000; var failure: Throwable? = null
        while (android.os.SystemClock.elapsedRealtime() < end) {
            try { check(); return } catch (error: Throwable) { failure = error }; Thread.sleep(100)
        }
        throw checkNotNull(failure)
    }
}
