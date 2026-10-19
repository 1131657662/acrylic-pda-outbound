package com.acrylic.outbound.pda

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.*
import androidx.test.espresso.matcher.ViewMatchers.*
import org.junit.Assert.*
import java.io.File

/** Real viewport checks scoped only to the TEMP-details product page. */
class TemporaryDetailsDeviceUi(private val output: File) {
    fun reveal(id: Int) {
        repeat(30) {
            settle()
            var visible = false; var below = true; var scrollY = 0
            onView(withId(id)).check { view, error ->
                if (error != null) throw error
                val bounds = safe(view); val p = IntArray(2); view.getLocationOnScreen(p)
                visible = p[1] >= bounds.first && p[1] + view.height <= bounds.second
                below = p[1] > (bounds.first + bounds.second) / 2
                var parent = view.parent
                while (parent != null) {
                    if (parent is ScrollView) { scrollY = parent.scrollY; break }
                    parent = parent.parent
                }
                output.mkdirs()
                File(output, "reach-$id.txt").appendText("y=${p[1]}; height=${view.height}; safe=$bounds; scrollY=$scrollY; visible=$visible\n")
            }
            if (visible) return
            drag(below)
        }
        inspect("unreachable-$id")
        error("Control $id cannot be reached inside system bars/IME")
    }

    fun click(id: Int) { reveal(id); onView(withId(id)).perform(ViewActions.click()) }

    fun inspectDialog(name: String) {
        onView(isRoot()).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check { view, error ->
            if (error != null) throw error
            val text = StringBuilder()
            inspectText(view, view.width, text)
            val button = view.findViewById<View>(android.R.id.button1)
            val p = IntArray(2); button.getLocationOnScreen(p)
            val bounds = safe(button)
            assertTrue(p[1] >= bounds.first && p[1] + button.height <= bounds.second)
            output.mkdirs(); File(output, "$name.txt").writeText(text.toString())
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { bitmap.recycle() }
        }
    }

    private fun drag(below: Boolean) {
        onView(isAssignableFrom(ScrollView::class.java)).perform(object : androidx.test.espresso.ViewAction {
            override fun getConstraints() = isDisplayed()
            override fun getDescription() = "Drag inside the safe viewport without fling or long press"
            override fun perform(controller: androidx.test.espresso.UiController, view: View) {
                val start = point(view, if (below) .9f else .1f)
                val end = point(view, if (below) .1f else .9f)
                val at = android.os.SystemClock.uptimeMillis()
                repeat(21) { step ->
                    val action = if (step == 0) android.view.MotionEvent.ACTION_DOWN else android.view.MotionEvent.ACTION_MOVE
                    val part = step / 20f
                    val event = android.view.MotionEvent.obtain(at, android.os.SystemClock.uptimeMillis(), action,
                        start[0], start[1] + (end[1] - start[1]) * part, 0)
                    try { check(controller.injectMotionEvent(event)) } finally { event.recycle() }
                    controller.loopMainThreadForAtLeast(15)
                }
                controller.loopMainThreadForAtLeast(100)
                val up = android.view.MotionEvent.obtain(at, android.os.SystemClock.uptimeMillis(), android.view.MotionEvent.ACTION_UP, end[0], end[1], 0)
                try { check(controller.injectMotionEvent(up)) } finally { up.recycle() }
                controller.loopMainThreadForAtLeast(500)
            }
        })
    }

    fun inspect(name: String) {
        settle()
        onView(isAssignableFrom(ScrollView::class.java)).check { view, error ->
            if (error != null) throw error
            val bars = checkNotNull(ViewCompat.getRootWindowInsets(view)).getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = checkNotNull(ViewCompat.getRootWindowInsets(view)).getInsets(WindowInsetsCompat.Type.ime())
            val content = checkNotNull(view.findViewById<LinearLayout>(R.id.content))
            assertTrue(view.paddingTop >= bars.top); assertTrue(view.paddingBottom >= bars.bottom)
            assertTrue(content.paddingBottom >= maxOf(0, ime.bottom - bars.bottom))
            val scroll = view as ScrollView
            val child = scroll.getChildAt(0)
            val text = StringBuilder("bars=$bars; ime=$ime; root=${scroll.width}x${scroll.height}; scrollY=${scroll.scrollY}; child=${child.width}x${child.height}@${child.top}; rootPadding=${scroll.paddingTop},${scroll.paddingBottom}; contentPadding=${content.paddingLeft},${content.paddingTop},${content.paddingRight},${content.paddingBottom}\n")
            inspectText(view, view.width, text)
            output.mkdirs(); File(output, "$name.txt").writeText(text.toString())
            val bitmap = Bitmap.createBitmap(view.rootView.width, view.rootView.height, Bitmap.Config.ARGB_8888)
            try {
                view.rootView.draw(Canvas(bitmap))
                File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { bitmap.recycle() }
        }
    }

    private fun inspectText(view: View, width: Int, text: StringBuilder) {
        if (view.visibility != View.VISIBLE) return
        if (view is TextView && view.text.isNotBlank()) {
            val layout = checkNotNull(view.layout)
            assertTrue("Horizontal overflow: ${view.text}", view.width <= width)
            if (view !is android.widget.EditText) for (i in 0 until layout.lineCount) {
                assertEquals("Ellipsized: ${view.text}", 0, layout.getEllipsisCount(i))
                assertTrue("Clipped text: ${view.text}", layout.getLineRight(i) <= view.width - view.compoundPaddingLeft - view.compoundPaddingRight + 2)
            }
            text.append("${view.id}: ${view.width}x${view.height} ${view.text}\n")
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) inspectText(view.getChildAt(i), width, text)
    }

    private fun safe(view: View): Pair<Int, Int> {
        val root = view.rootView; val insets = checkNotNull(ViewCompat.getRootWindowInsets(root))
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()); val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        val p = IntArray(2); root.getLocationOnScreen(p)
        return p[1] + bars.top to p[1] + root.height - maxOf(bars.bottom, ime.bottom)
    }
    private fun point(view: View, fraction: Float): FloatArray {
        val bounds = safe(view); val p = IntArray(2); view.getLocationOnScreen(p)
        return floatArrayOf(p[0] + view.width * .5f, bounds.first + (bounds.second - bounds.first) * fraction)
    }
    fun settle() {
        onView(isRoot()).perform(object : androidx.test.espresso.ViewAction {
            override fun getConstraints() = isRoot()
            override fun getDescription() = "Wait for TEMP page layout"
            override fun perform(controller: androidx.test.espresso.UiController, view: View) { controller.loopMainThreadForAtLeast(120) }
        })
    }
    fun await(check: () -> Unit) {
        var last: Throwable? = null
        repeat(80) { try { check(); return } catch (error: Throwable) { last = error }; settle() }
        throw checkNotNull(last)
    }
}
