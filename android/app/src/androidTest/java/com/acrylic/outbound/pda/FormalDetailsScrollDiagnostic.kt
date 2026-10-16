package com.acrylic.outbound.pda

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.ScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.ui.WorkDetailsActivity
import org.json.JSONObject
import org.junit.Test
import java.io.File

/** Diagnostic only: direct scroll is not a user-path acceptance result. */
class FormalDetailsScrollDiagnostic {
    @Test fun recordNativeScrollRangeAndFocus() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("formalDiagnostic") == "1702-read-only")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
        val identity = JSONObject(File(app.filesDir, "formal-details-input/identity.json").readText())
        check(identity.getBoolean("syntheticOnly"))
        val output = File(app.filesDir, "formal-details-diagnostic01").apply { check(!exists()); mkdirs() }
        val ui = FormalDetailsDeviceUi(output)
        ActivityScenario.launch<WorkDetailsActivity>(Intent(app, WorkDetailsActivity::class.java)
            .putExtra("assignmentId", identity.getString("assignmentId"))).use { scenario ->
            ui.await { onView(withId(R.id.retry)).check { view, _ -> check(view.isEnabled) } }
            facts(scenario, output, "before")
            scenario.onActivity { activity ->
                val next = activity.findViewById<android.view.View>(R.id.next)
                val scroll = next.rootView.findViewById<android.view.View>(android.R.id.content) as android.view.ViewGroup
                (scroll.getChildAt(0) as ScrollView).scrollTo(0, next.top)
            }
            ui.settle(); facts(scenario, output, "direct-scroll")
            scenario.onActivity { activity ->
                val next = activity.findViewById<android.view.View>(R.id.next)
                val rect = android.graphics.Rect(); next.getDrawingRect(rect)
                next.requestRectangleOnScreen(rect, true)
            }
            ui.settle(); facts(scenario, output, "rectangle")
        }
    }

    private fun facts(scenario: ActivityScenario<WorkDetailsActivity>, output: File, label: String) {
        scenario.onActivity { activity ->
            val next = activity.findViewById<android.view.View>(R.id.next)
            val holder = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            val scroll = holder.getChildAt(0) as ScrollView
            val position = IntArray(2); next.getLocationOnScreen(position)
            val json = JSONObject().put("diagnosticOnly", true).put("scrollHeight", scroll.height)
                .put("contentHeight", scroll.getChildAt(0).height).put("scrollY", scroll.scrollY)
                .put("nextTop", next.top).put("nextY", position[1]).put("nextHeight", next.height)
                .put("focusId", activity.currentFocus?.id).put("focusClass", activity.currentFocus?.javaClass?.name)
                .put("canDown", scroll.canScrollVertically(1)).put("canUp", scroll.canScrollVertically(-1))
            File(output, "$label.json").writeText(json.toString(2))
            val root = scroll.rootView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            try { root.draw(Canvas(bitmap)); File(output, "$label.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
            finally { bitmap.recycle() }
        }
    }
}
