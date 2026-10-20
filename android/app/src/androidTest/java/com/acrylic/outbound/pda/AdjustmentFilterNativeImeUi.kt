package com.acrylic.outbound.pda

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import org.json.JSONObject
import java.io.File

/** Uses the actual visible window, including adjustResize, without double-counting IME. */
internal class AdjustmentFilterNativeImeUi(private val evidence: File) {
    fun reveal(id: Int) {
        repeat(35) {
            var reached = false; var below = true
            onView(withId(id)).check { view, error ->
                if (error != null) throw error
                val bounds = safe(view); val p = IntArray(2); view.getLocationOnScreen(p)
                reached = p[1] >= bounds.top && p[1] + view.height <= bounds.bottom
                below = p[1] + view.height / 2 > bounds.centerY()
                File(evidence, "reach-" + id + ".txt").appendText("y=" + p[1] + ";h=" + view.height + ";safe=" + bounds + ";visible=" + reached + "\n")
            }
            if (reached) return
            drag(below)
        }
        error("Control " + id + " cannot be reached above the actual IME/system bars")
    }

    fun click(id: Int) { reveal(id); onView(withId(id)).perform(ViewActions.click()) }

    private fun safe(view: View): Rect {
        val root = view.rootView; val point = IntArray(2); root.getLocationOnScreen(point)
        val visible = Rect(); root.getWindowVisibleDisplayFrame(visible)
        val bars = checkNotNull(ViewCompat.getRootWindowInsets(root)).getInsets(WindowInsetsCompat.Type.systemBars())
        return Rect(maxOf(visible.left, point[0] + bars.left), maxOf(visible.top, point[1] + bars.top),
            minOf(visible.right, point[0] + root.width - bars.right), minOf(visible.bottom, point[1] + root.height - bars.bottom))
            .also { check(it.height() > 100 && it.width() > 100) }
    }

    private fun drag(below: Boolean) {
        onView(isAssignableFrom(ScrollView::class.java)).perform(object : ViewAction {
            override fun getConstraints() = isDisplayed()
            override fun getDescription() = "Drag within the actual visible window above native IME"
            override fun perform(controller: UiController, view: View) {
                val bounds = safe(view); val x = bounds.exactCenterX()
                val start = bounds.top + bounds.height() * if (below) .75f else .25f
                val end = bounds.top + bounds.height() * if (below) .25f else .75f
                val at = android.os.SystemClock.uptimeMillis()
                repeat(21) { step ->
                    val action = if (step == 0) android.view.MotionEvent.ACTION_DOWN else android.view.MotionEvent.ACTION_MOVE
                    inject(controller, at, action, x, start + (end - start) * step / 20f)
                    controller.loopMainThreadForAtLeast(15)
                }
                controller.loopMainThreadForAtLeast(100)
                inject(controller, at, android.view.MotionEvent.ACTION_UP, x, end)
                controller.loopMainThreadForAtLeast(250)
            }
        })
    }

    private fun inject(controller: UiController, at: Long, action: Int, x: Float, y: Float) {
        val event = android.view.MotionEvent.obtain(at, android.os.SystemClock.uptimeMillis(), action, x, y, 0)
        try { check(controller.injectMotionEvent(event)) } finally { event.recycle() }
    }

    fun capture(activity: Activity, name: String) {
        val root = activity.window.decorView; val bounds = safe(root)
        val insets = checkNotNull(ViewCompat.getRootWindowInsets(root))
        val geometry = JSONObject().put("visibleTop", bounds.top).put("visibleBottom", bounds.bottom)
            .put("rootHeight", root.height).put("imeBottom", insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
            .put("imeVisible", insets.isVisible(WindowInsetsCompat.Type.ime()))
        File(evidence, name + "-geometry.json").writeText(geometry.toString(2))
        val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
        try {
            root.draw(android.graphics.Canvas(bitmap))
            File(evidence, name + "-content.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    fun await(check: () -> Unit) {
        var last: Throwable? = null
        repeat(60) {
            try { check(); return } catch (error: AssertionError) { last = error }
            onView(isRoot()).perform(object : ViewAction {
                override fun getConstraints() = isRoot()
                override fun getDescription() = "Wait for native IME/filter state"
                override fun perform(controller: UiController, view: View) { controller.loopMainThreadForAtLeast(100) }
            })
        }
        throw checkNotNull(last)
    }
}
