package com.acrylic.outbound.pda

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.TextView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import java.io.File

/** Product view evidence with actual scroll visibility and full large-font text layout. */
internal object FormalFaultUiEvidence {
    fun await(check: () -> Unit) {
        val end = android.os.SystemClock.elapsedRealtime() + 20_000
        var failure: Throwable? = null
        while (android.os.SystemClock.elapsedRealtime() < end) {
            try { check(); return } catch (error: Throwable) { failure = error }
            Thread.sleep(100)
        }
        throw checkNotNull(failure)
    }

    fun click(id: Int) {
        reveal(id)
        Thread.sleep(500)
        onView(withId(id)).check(matches(isEnabled())).perform(click())
    }

    fun reveal(id: Int) {
        repeat(20) {
            if (runCatching { onView(withId(id)).check(matches(isCompletelyDisplayed())) }.isSuccess) return
            onView(isAssignableFrom(android.widget.ScrollView::class.java)).perform(if (id == R.id.back) swipeDown() else swipeUp())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(500)
        }
        onView(withId(id)).check(matches(isCompletelyDisplayed()))
    }

    fun screen(root: View, directory: File, label: String) {
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File(directory, "$label.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val rows = mutableListOf<String>()
        try { describe(root, rows) }
        finally { File(directory, "$label-views.txt").writeText(rows.joinToString("\n")) }
    }

    private fun describe(view: View, rows: MutableList<String>) {
        val visible = android.graphics.Rect(); view.getGlobalVisibleRect(visible)
        rows.add("${view.javaClass.simpleName}: ${view.width}x${view.height} visible=$visible text=${(view as? TextView)?.text}")
        if (view is TextView && view.isShown && view.layout != null) {
            for (line in 0 until view.layout.lineCount) assertEquals("Text must not be truncated", 0, view.layout.getEllipsisCount(line))
            assertTrue("Text height must fit: ${view.text}", view.layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
        }
        if (view is android.view.ViewGroup) for (i in 0 until view.childCount) describe(view.getChildAt(i), rows)
    }
}
