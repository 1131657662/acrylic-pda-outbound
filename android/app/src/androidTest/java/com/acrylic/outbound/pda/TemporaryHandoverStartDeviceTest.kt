package com.acrylic.outbound.pda

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.ui.HandoverActivity
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TemporaryHandoverStartDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as PdaApplication
    private val startLabel = "确认新资料并开启本轮"

    @Test fun productOpeningKeepsOriginalsAcrossCancelLostAckAndProcessRestart() {
        TemporaryHandoverStartUiFixture(app).use { f ->
            f.prepare()
            if (InstrumentationRegistry.getArguments().getString("d06StartUiPhase") == "restore") {
                restore(f)
                assertEquals(0, f.calls.get())
            } else {
                cancel(f)
                lostAck(f)
                recover(f)
                assertEquals(2, f.calls.get())
            }
            f.preserve()
            File(f.directory, "${InstrumentationRegistry.getArguments().getString("d06StartUiPhase")}-completed.txt")
                .writeText("Actual TEMP_START product page and navigation; synthetic identity, original signed ACK replay.\n")
        }
    }

    private fun screen() = ActivityScenario.launch<HandoverActivity>(Intent(app, HandoverActivity::class.java))
    private fun state(f: TemporaryHandoverStartUiFixture) = app.database.temporaryDrafts().get(f.client)!!.state

    private fun cancel(f: TemporaryHandoverStartUiFixture) {
        screen().use { scenario ->
            open()
            onView(withText("返回核对")).perform(scrollTo()).check(matches(isCompletelyDisplayed())).perform(click())
            assertEquals("HANDOVER_BLOCKED", state(f)); assertEquals(0, f.calls.get())
            screenshot(scenario, f, "cancelled")
            back()
        }
        f.preserve()
    }

    private fun lostAck(f: TemporaryHandoverStartUiFixture) {
        screen().use { scenario ->
            open()
            screenshot(scenario, f, "opening-dialog")
            onView(withText("确认资料并开启")).perform(scrollTo()).check(matches(isCompletelyDisplayed())).perform(click())
            await { onView(withId(R.id.status)).check(matches(withText(containsString("原资料与请求保留")))) }
            assertEquals("HANDOVER_BLOCKED", state(f))
            assertNull(app.database.temporary().prepared(f.activation.ticketId)!!.ackJson)
            assertEquals(1, f.calls.get())
            screenshot(scenario, f, "lost-ack")
            back()
        }
        f.preserve()
    }

    private fun recover(f: TemporaryHandoverStartUiFixture) {
        screen().use { scenario ->
            open()
            onView(withText("确认资料并开启")).perform(scrollTo()).check(matches(isCompletelyDisplayed())).perform(click())
            try { await { assertEquals("SCANNING", state(f)) } } catch (error: Throwable) {
                screenshot(scenario, f, "recovery-failed")
                scenario.onActivity { activity ->
                    File(f.directory, "recovery-status.txt").writeText(activity.findViewById<android.widget.TextView>(R.id.status).text.toString())
                }
                throw error
            }
            await { onView(withText("继续临时作业")).check(matches(isEnabled())) }
            assertNotNull(app.database.temporary().prepared(f.activation.ticketId)!!.ackJson)
            screenshot(scenario, f, "started")
            work(f)
            back()
        }
        f.otherIdentity()
        assertThrows(Exception::class.java) { app.temporaryStore().load(f.client) }
        f.restoreIdentity(); f.preserve()
    }

    private fun restore(f: TemporaryHandoverStartUiFixture) {
        assertEquals("SCANNING", state(f))
        val starts = app.database.temporaryScans().events(f.client).filter { it.type == "TEMP_HANDOVER_WORK_STARTED" }
        assertEquals(1, starts.size)
        screen().use { scenario ->
            await { onView(withText("继续临时作业")).check(matches(isEnabled())) }
            screenshot(scenario, f, "process-reopened")
            work(f)
            back()
        }
    }

    private fun work(f: TemporaryHandoverStartUiFixture) {
        reveal("继续临时作业", true)
        onView(withText("继续临时作业")).perform(click())
        await { onView(withId(R.id.vehicle)).check(matches(withText(containsString("临时单")))) }
        await { assertTrue(app.temporaryWork.state.value.ready) }
        assertEquals(f.client, app.temporaryWork.state.value.draft!!.clientOrderId)
        androidx.test.espresso.Espresso.pressBack()
        await { onView(withText("继续临时作业")).check(matches(isEnabled())) }
    }

    private fun open() {
        await { onView(withText(startLabel)).check(matches(isEnabled())) }
        reveal(startLabel, true)
        onView(withText(startLabel)).perform(click())
        await { onView(withText("核对临时交接新轮")).check(matches(isDisplayed())) }
    }
    private fun back() { reveal("返回（资料保留）", false); onView(withId(R.id.back)).perform(click()) }

    private fun reveal(text: String, up: Boolean) {
        repeat(20) {
            if (runCatching { onView(withText(text)).check(matches(isCompletelyDisplayed())) }.isSuccess) return
            onView(isAssignableFrom(android.widget.ScrollView::class.java)).perform(if (up) swipeUp() else swipeDown())
            Thread.sleep(250)
        }
        onView(withText(text)).check(matches(isCompletelyDisplayed()))
    }

    private fun screenshot(scenario: ActivityScenario<HandoverActivity>, f: TemporaryHandoverStartUiFixture, name: String) {
        scenario.onActivity { activity ->
            val root = activity.window.decorView
            val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(image))
            File(f.directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
    }

    private fun await(check: () -> Unit) {
        val end = android.os.SystemClock.elapsedRealtime() + 20_000; var failure: Throwable? = null
        while (android.os.SystemClock.elapsedRealtime() < end) {
            try { check(); return } catch (error: Throwable) { failure = error }
            Thread.sleep(100)
        }
        throw checkNotNull(failure)
    }
}
