package com.acrylic.outbound.pda

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.ui.MainActivity
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Original isolated identity; text is a noncredential; no provision, verify, revoke or import. */
class DataCredentialLayoutDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val run get() = InstrumentationRegistry.getArguments().getString("evidenceRun")!!

    @Test fun dataToCredentialKeyboardNavigationPreservesOriginalIdentity() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("dataCredentialLayout") == "true")
        check(run.matches(Regex("[a-z0-9-]{1,40}")))
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java)).use {
            settle(); tap(R.id.dataPage); tap(R.id.independentSku)
            await { onView(withId(R.id.choose)).check(matches(isEnabled())) }
            inspectAndSave("sku-top")
            tap(R.id.identity)
            await { onView(withId(R.id.reload)).check(matches(isEnabled())) }
            keyboardLayout()
            tap(R.id.back)
            onView(withId(R.id.back)).perform(ViewActions.scrollTo())
            settle()
            onView(withId(R.id.back)).check(matches(isCompletelyDisplayed())).perform(ViewActions.click())
            onView(withId(R.id.dataPage)).check(matches(isSelected()))
        }
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        File(app.filesDir, "data-credential-evidence/$run/completed.txt").writeText("Actual Data -> SKU -> Identity -> keyboard text -> Back; no credential commands or import.\n")
    }

    private fun keyboardLayout() {
        reveal(R.id.password)
        onView(withId(R.id.password)).perform(ViewActions.click())
        await {
            onView(isRoot()).check { view, error ->
                if (error != null) throw error
                assertTrue("Actual keyboard not yet visible", ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true)
            }
        }
        settle()
        inspectAndSave("keyboard-open", true)
        reveal(R.id.password)
        onView(withId(R.id.password)).perform(ViewActions.typeTextIntoFocusedView("layout-check-only"))
        settle()
        onView(withId(R.id.independentPasswordLabel)).check { view, error ->
            if (error != null) throw error
            assertEquals("独立口令", (view as TextView).text.toString())
            assertEquals(R.id.password, view.labelFor)
        }
        reveal(R.id.verify)
        inspectAndSave("keyboard-verify", true)
        onView(withId(R.id.verify)).check { view, error ->
            if (error != null) throw error
            val root = view.rootView
            val ime = checkNotNull(ViewCompat.getRootWindowInsets(root)).getInsets(WindowInsetsCompat.Type.ime())
            val p = IntArray(2); view.getLocationInWindow(p)
            assertTrue("Verify action is covered by keyboard", p[1] + view.height <= root.height - ime.bottom)
        }
        reveal(R.id.password)
        onView(withId(R.id.password)).perform(ViewActions.replaceText(""), ViewActions.closeSoftKeyboard())
        settle(); inspectAndSave("keyboard-hidden")
    }

    private fun tap(id: Int) { reveal(id); onView(withId(id)).perform(ViewActions.click()); settle() }

    private fun reveal(id: Int) {
        repeat(25) {
            settle()
            if (runCatching { onView(withId(id)).check(matches(isCompletelyDisplayed())) }.isSuccess) return
            var below = true
            onView(withId(id)).perform(object : androidx.test.espresso.ViewAction {
                override fun getConstraints() = withEffectiveVisibility(Visibility.VISIBLE)
                override fun getDescription() = "Read position without invoking action"
                override fun perform(controller: androidx.test.espresso.UiController, view: View) {
                    val root = view.rootView
                    val insets = checkNotNull(ViewCompat.getRootWindowInsets(root))
                    val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
                    val bottom = maxOf(insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom,
                        insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
                    val p = IntArray(2); view.getLocationInWindow(p)
                    below = p[1] > (top + root.height - bottom) / 2
                }
            })
            onView(isAssignableFrom(ScrollView::class.java)).perform(GeneralSwipeAction(Swipe.SLOW,
                { v -> point(v, if (below) 0.8f else 0.25f) },
                { v -> point(v, if (below) 0.25f else 0.8f) }, Press.FINGER))
        }
        onView(withId(id)).check(matches(isCompletelyDisplayed()))
    }

    private fun point(view: View, fraction: Float): FloatArray {
        val p = IntArray(2); view.getLocationOnScreen(p)
        val ime = ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
        return floatArrayOf(p[0] + view.width * 0.5f, p[1] + (view.height - ime).coerceAtLeast(1) * fraction)
    }

    private fun settle() {
        onView(isRoot()).perform(object : androidx.test.espresso.ViewAction {
            override fun getConstraints() = isRoot()
            override fun getDescription() = "Wait for real scroll animation"
            override fun perform(controller: androidx.test.espresso.UiController, view: View) { controller.loopMainThreadForAtLeast(1000) }
        })
    }

    private fun inspectAndSave(name: String, keyboardExpected: Boolean = false) {
        onView(isAssignableFrom(ScrollView::class.java)).perform(object : androidx.test.espresso.ViewAction {
            override fun getConstraints() = isDisplayed()
            override fun getDescription() = "Inspect actual safe viewport and text; retain secure view rendering"
            override fun perform(controller: androidx.test.espresso.UiController, view: View) {
                val bars = checkNotNull(ViewCompat.getRootWindowInsets(view)).getInsets(WindowInsetsCompat.Type.systemBars())
                assertTrue("Top bar overlaps content", view.paddingTop >= bars.top)
                assertTrue("Bottom bar overlaps content", view.paddingBottom >= bars.bottom)
                val ime = checkNotNull(ViewCompat.getRootWindowInsets(view)).getInsets(WindowInsetsCompat.Type.ime())
                if (keyboardExpected) {
                    assertTrue("Actual keyboard was not opened", ime.bottom > 0)
                    assertTrue("Keyboard overlaps scroll content", view.paddingBottom >= ime.bottom)
                }
                inspect(view, view.width)
                val directory = File(app.filesDir, "data-credential-evidence/$run").apply { mkdirs() }
                File(directory, "$name-insets.txt").writeText("bars=$bars; ime=$ime; padding=${view.paddingTop},${view.paddingBottom}; size=${view.width},${view.height}")
                val display = view.rootView
                val bitmap = Bitmap.createBitmap(display.width, display.height, Bitmap.Config.ARGB_8888)
                try {
                    display.draw(Canvas(bitmap))
                    File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } finally { bitmap.recycle() }
            }
        })
    }

    private fun inspect(view: View, width: Int) {
        if (!view.isShown) return
        assertTrue("Horizontal overflow: ${view.javaClass.simpleName}", view.width <= width)
        if (view is TextView && view.layout != null) {
            for (line in 0 until view.layout.lineCount) assertEquals("Text ellipsis: ${view.text}", 0, view.layout.getEllipsisCount(line))
            assertTrue("Text clipped: ${view.text}", view.layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) inspect(view.getChildAt(i), width)
    }
    private fun await(check: () -> Unit) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 30000
        var failure: Throwable? = null
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            try { check(); return } catch (error: Throwable) { failure = error }
            Thread.sleep(100)
        }
        throw checkNotNull(failure)
    }
}
