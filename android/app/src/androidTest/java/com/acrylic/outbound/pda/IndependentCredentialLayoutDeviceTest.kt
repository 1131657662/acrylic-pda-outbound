package com.acrylic.outbound.pda

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.action.Press
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.ui.IndependentCredentialActivity
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Direct read-only layout check; no provisioning, verification or revocation command. */
class IndependentCredentialLayoutDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val run get() = InstrumentationRegistry.getArguments().getString("evidenceRun")!!

    @Test fun credentialLabelAndKeyboardKeepActionsReachable() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("credentialLayout") == "true")
        check(run.matches(Regex("[a-z0-9-]{1,40}")))
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        ActivityScenario.launch<IndependentCredentialActivity>(Intent(app, IndependentCredentialActivity::class.java)).use { scenario ->
            await { onView(withId(R.id.password)).check(matches(isEnabled())) }
            inspect("top", false)
            reveal(R.id.password)
            onView(withId(R.id.password)).perform(click())
            await {
                onView(isRoot()).check { root, error ->
                    if (error != null) throw error
                    assertTrue(ViewCompat.getRootWindowInsets(root)?.isVisible(WindowInsetsCompat.Type.ime()) == true)
                }
            }
            settle()
            reveal(R.id.password)
            onView(withId(R.id.password)).perform(typeTextIntoFocusedView("layout-check-only"))
            onView(withId(R.id.independentPasswordLabel)).check(matches(withText("独立口令")))
            reveal(R.id.verify)
            onView(withId(R.id.verify)).check(matches(isCompletelyDisplayed()))
            inspect("keyboard", true)
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            settle()
            inspect("closed", false)
            reveal(R.id.password)
            onView(withId(R.id.password)).perform(replaceText(""))
            reveal(R.id.back)
            scenario.onActivity { assertTrue(it.window.decorView.width <= 480) }
            onView(withId(R.id.back)).perform(click())
        }
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        File(app.filesDir, "credential-layout-evidence/$run/completed.txt").apply { parentFile!!.mkdirs(); writeText("Direct credential layout; keyboard; no business command.\n") }
    }

    private fun reveal(id: Int) {
        repeat(12) {
            settle()
            var visible = false
            var below = true
            onView(withId(id)).check { view, error ->
                if (error != null) throw error
                val bounds = safeBounds(view)
                val p = IntArray(2); view.getLocationOnScreen(p)
                visible = p[1] >= bounds.first && p[1] + view.height <= bounds.second
                below = p[1] > (bounds.first + bounds.second) / 2
            }
            if (visible) return
            onView(isAssignableFrom(ScrollView::class.java)).perform(GeneralSwipeAction(Swipe.SLOW,
                { v -> point(v, if (below) .8f else .2f) },
                { v -> point(v, if (below) .2f else .8f) }, Press.FINGER))
        }
        error("Control $id is unreachable in the real safe viewport")
    }

    private fun safeBounds(view: View): Pair<Int, Int> {
        val root = view.rootView
        val insets = checkNotNull(ViewCompat.getRootWindowInsets(root))
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        val p = IntArray(2); root.getLocationOnScreen(p)
        return p[1] + bars.top to p[1] + root.height - maxOf(bars.bottom, ime.bottom)
    }

    private fun point(view: View, fraction: Float): FloatArray {
        val bounds = safeBounds(view)
        val p = IntArray(2); view.getLocationOnScreen(p)
        return floatArrayOf(p[0] + view.width * .5f, bounds.first + (bounds.second - bounds.first) * fraction)
    }

    private fun inspect(name: String, keyboard: Boolean) {
        onView(isAssignableFrom(ScrollView::class.java)).perform(object : androidx.test.espresso.ViewAction {
            override fun getConstraints() = isDisplayed()
            override fun getDescription() = "Inspect credential layout and insets"
            override fun perform(controller: androidx.test.espresso.UiController, view: View) {
                val insets = checkNotNull(ViewCompat.getRootWindowInsets(view))
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
                if (keyboard) assertTrue(ime.bottom > 0)
                inspectText(view, view.width)
                assertTrue(view.paddingTop >= bars.top)
                assertTrue(view.paddingBottom >= maxOf(bars.bottom, ime.bottom))
                val directory = File(app.filesDir, "credential-layout-evidence/$run").apply { mkdirs() }
                directory.resolve("$name-insets.txt").writeText("bars=$bars; ime=$ime; padding=${view.paddingTop},${view.paddingBottom}")
                saveImage(view.rootView, directory.resolve("$name.png"))
            }
        })
    }

    private fun saveImage(view: View, file: File) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    private fun settle() {
        onView(isRoot()).perform(object : androidx.test.espresso.ViewAction {
            override fun getConstraints() = isRoot()
            override fun getDescription() = "Wait for actual keyboard and scroll animation"
            override fun perform(controller: androidx.test.espresso.UiController, view: View) {
                controller.loopMainThreadForAtLeast(1000)
            }
        })
    }

    private fun inspectText(view: View, width: Int) {
        if (!view.isShown) return
        assertTrue(view.width <= width)
        if (view is TextView && view.layout != null) {
            for (line in 0 until view.layout.lineCount) assertEquals(0, view.layout.getEllipsisCount(line))
            assertTrue(view.layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) inspectText(view.getChildAt(i), width)
    }

    private fun await(check: () -> Unit) {
        val end = android.os.SystemClock.elapsedRealtime() + 30000
        var failure: Throwable? = null
        while (android.os.SystemClock.elapsedRealtime() < end) {
            try { check(); return } catch (error: Throwable) { failure = error }
            Thread.sleep(100)
        }
        throw checkNotNull(failure)
    }
}
