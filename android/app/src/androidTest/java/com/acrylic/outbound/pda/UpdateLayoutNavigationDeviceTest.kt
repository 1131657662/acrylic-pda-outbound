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

/** Uses original isolated identity, reads layout only, never checks or installs an update. */
class UpdateLayoutNavigationDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val run get() = InstrumentationRegistry.getArguments().getString("evidenceRun")!!

    @Test fun originalMyPageUpdateNavigationRespectsSystemBarsAndRetainsData() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("updateLayout") == "true")
        check(run.matches(Regex("[a-z0-9-]{1,40}")))
        check(app.updates.savedState() == null) { "Preserve pending original update; do not alter it for this test" }
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java)).use {
            settle(); tap(R.id.myPage); tap(R.id.appUpdate)
            onView(withId(R.id.currentVersion)).check(matches(withText(containsString(BuildConfig.VERSION_NAME))))
            onView(withId(R.id.lastCheck)).check(matches(withText("尚未完成更新检查")))
            onView(withId(R.id.status)).check(matches(withText(containsString("不会自动下载或安装"))))
            onView(withId(R.id.download)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(withId(R.id.install)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            inspectAndSave("top")
            reveal(R.id.check)
            onView(withId(R.id.check)).check(matches(isEnabled()))
            inspectAndSave("check-visible")
            tap(R.id.back)
            onView(withId(R.id.myPage)).check(matches(isEnabled()))
        }
        assertNull(app.updates.savedState())
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        File(app.filesDir, "update-layout-evidence/$run/completed.txt").writeText("Actual My -> Update -> Back; no check, download or installation.\n")
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
                    val p = IntArray(2); view.getLocationOnScreen(p); below = p[1] > view.rootView.height / 2
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
        return floatArrayOf(p[0] + view.width * 0.5f, p[1] + view.height * fraction)
    }

    private fun settle() {
        onView(isRoot()).perform(object : androidx.test.espresso.ViewAction {
            override fun getConstraints() = isRoot()
            override fun getDescription() = "Wait for real scroll animation"
            override fun perform(controller: androidx.test.espresso.UiController, view: View) { controller.loopMainThreadForAtLeast(1000) }
        })
    }

    private fun inspectAndSave(name: String) {
        onView(isAssignableFrom(ScrollView::class.java)).perform(object : androidx.test.espresso.ViewAction {
            override fun getConstraints() = isDisplayed()
            override fun getDescription() = "Inspect actual safe viewport and text; retain secure view rendering"
            override fun perform(controller: androidx.test.espresso.UiController, view: View) {
                val bars = checkNotNull(ViewCompat.getRootWindowInsets(view)).getInsets(WindowInsetsCompat.Type.systemBars())
                assertTrue("Top bar overlaps content", view.paddingTop >= bars.top)
                assertTrue("Bottom bar overlaps content", view.paddingBottom >= bars.bottom)
                inspect(view, view.width)
                val directory = File(app.filesDir, "update-layout-evidence/$run").apply { mkdirs() }
                File(directory, "$name-insets.txt").writeText("bars=$bars; padding=${view.paddingTop},${view.paddingBottom}; size=${view.width},${view.height}")
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
}
