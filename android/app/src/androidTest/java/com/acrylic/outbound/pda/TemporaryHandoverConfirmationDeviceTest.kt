package com.acrylic.outbound.pda

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.ui.HandoverActivity
import org.hamcrest.CoreMatchers.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Product page operation; HTTP itself is validated separately, this replays its exact signed original. */
class TemporaryHandoverConfirmationDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as PdaApplication

    @Test fun explicitDialogReturnsWithoutSendingThenRestoresSameConfirmation() {
        TemporaryHandoverUiFixture(app).use { f ->
            f.prepare()
            val before = checkNotNull(app.database.temporaryHandoverConfirmations().get(f.id))
            val original = app.database.temporaryHandoverStages().get(f.id)!!.payloadBytes
            ActivityScenario.launch<HandoverActivity>(Intent(app, HandoverActivity::class.java)).use { scenario ->
                await { onView(withText("恢复原接收确认")).check(matches(isEnabled())) }
                onView(withText("恢复原接收确认")).perform(scrollTo(), click())
                onView(withText("确认临时交接实物与资料")).check { view, error ->
                    if (error != null) throw error
                    screen(view.rootView, f, "confirmation-dialog")
                }
                onView(withText("返回核对")).perform(click())
                assertEquals(0, f.calls.get())
                assertEquals(before, app.database.temporaryHandoverConfirmations().get(f.id))
                onView(withText("恢复原接收确认")).perform(scrollTo(), click())
                onView(withText("已核对，确认接收")).perform(click())
                await { onView(withText(containsString("接收确认已保存"))).check(matches(isDisplayed())) }
                scenario.onActivity { screen(it.window.decorView, f, "confirmed") }
                onView(withId(R.id.back)).perform(scrollTo(), click())
            }
            assertEquals("CONFIRMED", app.database.temporaryHandoverConfirmations().get(f.id)!!.state)
            assertEquals(before.commandId, app.database.temporaryHandoverConfirmations().get(f.id)!!.commandId)
            assertArrayEquals(original, app.database.temporaryHandoverStages().get(f.id)!!.payloadBytes)
            assertTrue(f.calls.get() > 0)
            f.otherIdentity()
            ActivityScenario.launch<HandoverActivity>(Intent(app, HandoverActivity::class.java)).use { scenario ->
                await { onView(withId(R.id.status)).check(matches(withText(containsString("本机资料已保留")))) }
                scenario.onActivity { activity ->
                    assertEquals(0, activity.findViewById<android.widget.LinearLayout>(R.id.cards).childCount)
                    screen(activity.window.decorView, f, "other-identity")
                }
                onView(withId(R.id.back)).perform(scrollTo(), click())
            }
            File(f.directory, "completed.marker").writeText("Dialog cancel, original confirmation, preserved stage, return and identity isolation passed.\n")
        }
    }

    @Test fun confirmedCardRemainsReadableAtLargeFont() {
        TemporaryHandoverUiFixture(app).use { f ->
            f.prepare()
            assertEquals("CONFIRMED", app.database.temporaryHandoverConfirmations().get(f.id)!!.state)
            ActivityScenario.launch<HandoverActivity>(Intent(app, HandoverActivity::class.java)).use { scenario ->
                await { onView(withText(containsString("接收确认已保存"))).check(matches(withEffectiveVisibility(Visibility.VISIBLE))) }
                reveal("核对并保存激活结果", upwards = true)
                scenario.onActivity { activity ->
                    val cards = activity.findViewById<android.widget.LinearLayout>(R.id.cards)
                    val card = cards.getChildAt(0)
                    for (id in listOf(R.id.title, R.id.details, R.id.action)) {
                        val view = card.findViewById<TextView>(id)
                        for (line in 0 until view.layout.lineCount) assertEquals(0, view.layout.getEllipsisCount(line))
                        assertTrue(view.layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
                    }
                    screen(activity.window.decorView, f, "large-font")
                }
                reveal("返回（资料保留）", upwards = false)
                onView(withId(R.id.back)).perform(click())
            }
        }
    }

    private fun reveal(text: String, upwards: Boolean) {
        repeat(20) {
            if (runCatching { onView(withText(text)).check(matches(isCompletelyDisplayed())) }.isSuccess) return
            onView(isAssignableFrom(android.widget.ScrollView::class.java)).perform(if (upwards) swipeUp() else swipeDown())
        }
        onView(withText(text)).check(matches(isCompletelyDisplayed()))
    }

    private fun screen(view: View, f: TemporaryHandoverUiFixture, label: String) {
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        File(f.directory, "$label.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
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
