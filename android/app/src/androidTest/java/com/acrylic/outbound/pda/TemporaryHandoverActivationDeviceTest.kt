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
import com.acrylic.outbound.pda.ui.HandoverActivity
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Product installation and restart using the original signed HTTP result, with synthetic emulator identity. */
class TemporaryHandoverActivationDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as PdaApplication

    @Test fun installsOriginalActivationAndRestoresBlockedDraftAfterPageRestart() {
        TemporaryHandoverUiFixture(app).use { f ->
            f.prepare()
            val original = app.database.temporaryHandoverStages().get(f.id)!!.payloadBytes
            assertNull(app.database.temporaryHandoverActivations().get(f.id))
            ActivityScenario.launch<HandoverActivity>(Intent(app, HandoverActivity::class.java)).use { scenario ->
                await { onView(withText("核对并保存激活结果")).check(matches(isEnabled())) }
                reveal("核对并保存激活结果", upwards = true)
                onView(withText("核对并保存激活结果")).perform(click())
                await { onView(withText(containsString("本机资料已保留"))).check(matches(withEffectiveVisibility(Visibility.VISIBLE))) }
                screenshot(scenario, f, "activation-installed")
                reveal("返回（资料保留）", upwards = false)
                onView(withId(R.id.back)).perform(click())
            }
            await { assertNotNull(app.database.temporaryHandoverActivations().get(f.id)) }
            val saved = app.database.temporaryHandoverActivations().get(f.id)!!
            assertEquals("HANDOVER_BLOCKED", app.database.temporaryDrafts().get(saved.newClientOrderId)!!.state)
            assertArrayEquals(original, app.database.temporaryHandoverStages().get(f.id)!!.payloadBytes)
            restore(f, saved.receiptBytes)
            File(f.directory, "activation-completed.marker").writeText("Product installation, readonly originals, restart, blocked state, large-font layout and return passed.\n")
        }
    }

    private fun restore(f: TemporaryHandoverUiFixture, bytes: ByteArray) {
        ActivityScenario.launch<HandoverActivity>(Intent(app, HandoverActivity::class.java)).use { scenario ->
            await { onView(withText("核对原激活结果")).check(matches(isEnabled())) }
            reveal("核对原激活结果", upwards = true)
            onView(withText("核对原激活结果")).perform(click())
            await { onView(withText(containsString("本机资料已保留"))).check(matches(withEffectiveVisibility(Visibility.VISIBLE))) }
            assertArrayEquals(bytes, app.database.temporaryHandoverActivations().get(f.id)!!.receiptBytes)
            screenshot(scenario, f, "activation-reopened")
            reveal("返回（资料保留）", upwards = false)
            onView(withId(R.id.back)).perform(click())
        }
        assertEquals(1, f.calls.get())
    }

    private fun screenshot(scenario: ActivityScenario<HandoverActivity>, f: TemporaryHandoverUiFixture, name: String) {
        scenario.onActivity { activity ->
            val card = activity.findViewById<android.widget.LinearLayout>(R.id.cards).getChildAt(0)
            for (id in listOf(R.id.title, R.id.details, R.id.action)) {
                val view = card.findViewById<TextView>(id)
                for (line in 0 until view.layout.lineCount) assertEquals(0, view.layout.getEllipsisCount(line))
                assertTrue(view.layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
            }
            val view = activity.window.decorView
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            File(f.directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
    }

    private fun reveal(text: String, upwards: Boolean) {
        repeat(20) {
            if (runCatching { onView(withText(text)).check(matches(isCompletelyDisplayed())) }.isSuccess) return
            onView(isAssignableFrom(android.widget.ScrollView::class.java)).perform(if (upwards) swipeUp() else swipeDown())
        }
        onView(withText(text)).check(matches(isCompletelyDisplayed()))
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
