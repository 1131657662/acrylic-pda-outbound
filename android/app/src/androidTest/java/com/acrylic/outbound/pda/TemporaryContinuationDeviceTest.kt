package com.acrylic.outbound.pda

import android.app.Activity
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
import com.acrylic.outbound.pda.ui.*
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Actual page actions on a fresh emulator; synthetic identities, copied real-HTTP Room input, replayed network. */
class TemporaryContinuationDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as PdaApplication

    @Test fun confirmContinueScanFreezeAndRecoverWrongIdentityThroughProductPages() {
        TemporaryContinuationUiFixture(app).use { f ->
            f.prepare()
            val originalId = app.database.temporaryContinuations().forTarget(f.id)!!.sourceSubmissionId
            val original = app.database.temporarySubmissions().get(originalId)
            confirm(f)
            scan(f)
            freeze(f)
            wrongIdentity(f)
            assertEquals(original, app.database.temporarySubmissions().get(originalId))
            File(f.directory, "completed.json").writeText(JSONObject().put("productPages", true)
                .put("originalPreserved", true).put("productionRelease", false).toString())
        }
    }

    @Test fun frozenContinuationRemainsReadableAtLargeFont() {
        TemporaryContinuationUiFixture(app).use { f ->
            f.prepare()
            val original = app.database.temporarySubmissions().forOrder(f.id)
            assertNotNull(original)
            ActivityScenario.launch<TemporaryContinuationActivity>(Intent(app, TemporaryContinuationActivity::class.java)
                .putExtra("clientOrderId", f.id)).use { scenario ->
                await { onView(withId(R.id.status)).check(matches(withText(containsString("本轮已冻结")))) }
                for (id in listOf(R.id.summary, R.id.status, R.id.confirm, R.id.open)) {
                    scenario.onActivity { activity ->
                        val view = activity.findViewById<TextView>(id)
                        (view.parent.parent as android.widget.ScrollView).scrollTo(0, view.top)
                    }
                    instrumentation.waitForIdleSync()
                    onView(withId(id)).check(matches(isDisplayed()))
                    scenario.onActivity { activity ->
                        val view = activity.findViewById<TextView>(id)
                        val visible = android.graphics.Rect()
                        assertTrue(view.getGlobalVisibleRect(visible)); assertEquals(view.height, visible.height())
                        assertTrue(view.layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
                        for (line in 0 until view.layout.lineCount) assertEquals(0, view.layout.getEllipsisCount(line))
                    }
                }
                scenario.onActivity { screen(it, f, "large-font") }
                onView(withId(R.id.confirm)).check(matches(not(isEnabled())))
                scenario.onActivity { activity ->
                    (activity.findViewById<TextView>(R.id.back).parent.parent as android.widget.ScrollView).scrollTo(0, 0)
                }
                instrumentation.waitForIdleSync()
                onView(withId(R.id.back)).perform(click())
            }
            assertEquals(original, app.database.temporarySubmissions().forOrder(f.id))
        }
    }

    private fun confirm(f: TemporaryContinuationUiFixture) {
        ActivityScenario.launch<TemporaryContinuationActivity>(Intent(app, TemporaryContinuationActivity::class.java)
            .putExtra("clientOrderId", f.id)).use { scenario ->
            await { onView(withId(R.id.confirm)).check(matches(isEnabled())) }
            onView(withId(R.id.confirm)).perform(scrollTo())
            scenario.onActivity { screen(it, f, "confirmation") }
            onView(withId(R.id.confirm)).perform(click())
            onView(withText("确认恢复")).perform(click())
            await { onView(withId(R.id.status)).check(matches(withText(containsString("可继续本机装车")))) }
            onView(withId(R.id.open)).perform(scrollTo(), click())
        }
    }

    private fun scan(f: TemporaryContinuationUiFixture) {
        await { assertTrue(app.temporaryWork.state.value.ready) }
        onView(withId(R.id.manualToggle)).perform(click())
        onView(withId(R.id.manual)).perform(replaceText("00100000005"), closeSoftKeyboard())
        onView(withId(R.id.validate)).perform(click())
        await { assertEquals("WAITING_CONFIRM", app.temporaryWork.state.value.candidate?.state) }
        onView(withId(R.id.confirm)).perform(click())
        await { assertEquals(4L, app.temporaryWork.state.value.total.count) }
        onView(withId(R.id.closeOrder)).perform(scrollTo(), click())
        await { onView(withId(R.id.summary)).check(matches(withText(containsString("实装 4 包")))) }
        onView(withId(R.id.summary)).perform(scrollTo())
        resumed { screen(it, f, "before-freeze") }
    }

    private fun freeze(f: TemporaryContinuationUiFixture) {
        onView(withId(R.id.freeze)).perform(scrollTo(), click())
        onView(withText("确认冻结")).perform(click())
        await { onView(withId(R.id.status)).check(matches(withText(containsString("已冻结")))) }
        onView(withId(R.id.status)).perform(scrollTo())
        resumed { screen(it, f, "frozen") }
        val frozen = checkNotNull(app.database.temporarySubmissions().forOrder(f.id))
        assertEquals("4", JSONObject(frozen.payload).getString("expectedCount"))
    }

    private fun wrongIdentity(f: TemporaryContinuationUiFixture) {
        f.otherIdentity()
        ActivityScenario.launch<TemporarySubmissionActivity>(Intent(app, TemporarySubmissionActivity::class.java)
            .putExtra("clientOrderId", f.id)).use { scenario ->
            await { onView(withId(R.id.status)).check(matches(withText(containsString("原记录核对未通过")))) }
            onView(withId(R.id.upload)).check(matches(not(isEnabled())))
            onView(withId(R.id.status)).perform(scrollTo())
            scenario.onActivity { screen(it, f, "identity-blocked") }
            onView(withId(R.id.back)).perform(scrollTo(), click())
        }
    }

    private fun screen(activity: Activity, f: TemporaryContinuationUiFixture, name: String) {
        val root = activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File(f.directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val state = JSONObject()
        for (id in listOf(R.id.summary, R.id.status, R.id.error))activity.findViewById<TextView>(id)?.let {
            state.put(activity.resources.getResourceEntryName(id), it.text.toString())
        }
        File(f.directory, "$name.json").writeText(state.toString())
    }

    private fun resumed(action: (Activity) -> Unit) = instrumentation.runOnMainSync {
        action(androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).single())
    }

    private fun await(check: () -> Unit) {
        val end = android.os.SystemClock.elapsedRealtime() + 20_000
        var failure: Throwable? = null
        while (android.os.SystemClock.elapsedRealtime() < end) {
            try { check(); return } catch (error: Throwable) { failure = error }
            Thread.sleep(100)
        }
        throw checkNotNull(failure)
    }
}
