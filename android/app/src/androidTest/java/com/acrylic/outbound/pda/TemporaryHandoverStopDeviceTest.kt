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

/** Actual product activity, synthetic identity and exact preserved signed HTTP response. */
class TemporaryHandoverStopDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication

    @Test fun independentReceivingStopRequiresReasonKeepsOriginalsAndSurvivesReopen() {
        TemporaryHandoverStopUiFixture(app).use { f ->
            f.prepare()
            val before = checkNotNull(app.database.temporarySubmissions().forOrder(f.id))
            val intent = Intent(app, TemporarySubmissionActivity::class.java).putExtra("clientOrderId", f.id)
            ActivityScenario.launch<TemporarySubmissionActivity>(intent).use { screen ->
                status("待独立确认")
                openStop(screen, f); confirmStop(f)
                await { onView(withId(R.id.error)).check(matches(withText(containsString("停止须填写")))) }
                assertNull(app.database.temporarySubmissions().get(before.id)!!.abortReason)
                openStop(screen, f)
                onView(withHint("停止原因")).perform(scrollTo(), replaceText(f.input.getJSONObject("request").getString("reason")), closeSoftKeyboard())
                confirmStop(f); status("停止回执已验签")
                reveal("停止回执已验签", upwards = false)
                screen.onActivity { screenshot(it, f, "stopped") }
                val stopped = checkNotNull(app.database.temporarySubmissions().get(before.id))
                assertEquals("ABORTED", stopped.state); assertEquals(before.payload, stopped.payload)
                assertFalse(stopped.autoRetry); assertNull(app.database.temporarySuccess().evidence(before.id))
                reveal("返回临时订单", upwards = false); onView(withId(R.id.back)).perform(click())
            }
            ActivityScenario.launch<TemporarySubmissionActivity>(intent).use { screen ->
                status("停止回执已验签")
                screen.onActivity { screenshot(it, f, "reopened") }
                assertEquals(1, f.calls.get()); onView(withId(R.id.back)).perform(click())
            }
            File(f.directory, "completed.txt").writeText("Actual receiving STOP page, original reason/receipt/payload, closed R06 flag, reopen and return; synthetic identity and HTTP replay.")
        }
    }

    private fun openStop(screen: ActivityScenario<TemporarySubmissionActivity>, f: TemporaryHandoverStopUiFixture) {
        reveal("停止本轮临时交接", upwards = true)
        screen.onActivity { screenshot(it, f, "before-stop") }
        val stop = withText("停止本轮临时交接 · 保留实物占用")
        await { onView(stop).check(matches(isEnabled())) }
        onView(stop).perform(click())
        try { await { onView(withHint("停止原因")).check(matches(isDisplayed())) } }
        catch (error: Throwable) { screen.onActivity { screenshot(it, f, "failed-stop") }; throw error }
    }

    private fun confirmStop(f: TemporaryHandoverStopUiFixture) {
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        val confirm = withText("确认停止")
        onView(confirm).perform(scrollTo())
        onView(confirm).perform(object : androidx.test.espresso.ViewAction {
            override fun getConstraints() = isAssignableFrom(android.view.View::class.java)
            override fun getDescription() = "Save actual dialog layout without clicking"
            override fun perform(controller: androidx.test.espresso.UiController, view: android.view.View) {
                snapshot(view.rootView, f, "dialog-before-confirm")
            }
        })
        await { onView(confirm).check(matches(isCompletelyDisplayed())) }
        onView(confirm).perform(click())
    }

    private fun reveal(text: String, upwards: Boolean) {
        repeat(20) {
            if (runCatching { onView(withText(containsString(text))).check(matches(isCompletelyDisplayed())) }.isSuccess) return
            onView(isAssignableFrom(android.widget.ScrollView::class.java)).perform(if (upwards) swipeUp() else swipeDown())
            Thread.sleep(500)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
        onView(withText(containsString(text))).check(matches(isCompletelyDisplayed()))
    }

    private fun status(text: String) = await {
        onView(withId(R.id.status)).check(matches(withText(containsString(text))))
    }

    private fun screenshot(activity: TemporarySubmissionActivity, f: TemporaryHandoverStopUiFixture, name: String) {
        snapshot(activity.window.decorView, f, name)
        File(f.directory, "$name.txt").writeText(activity.findViewById<TextView>(R.id.status).text.toString())
    }

    private fun snapshot(root: android.view.View, f: TemporaryHandoverStopUiFixture, name: String) {
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888); root.draw(Canvas(bitmap))
        File(f.directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val rows = mutableListOf<String>(); describe(root, rows)
        File(f.directory, "$name-views.txt").writeText(rows.joinToString("\n"))
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
