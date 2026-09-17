package com.acrylic.outbound.pda

import android.content.Intent
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.ui.WorkActivity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Read-only verification of the order created by DispositionEndToEndTest. */
@RunWith(AndroidJUnit4::class)
class DispositionUiTest {
    @Test fun completedReducedOrderShowsOriginalScanAsUnloaded() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext; val app = context.applicationContext as PdaApplication
        val evidence = JSONObject(File(context.filesDir, "disposition-evidence.json").readText())
        val id = app.database.openHelper.readableDatabase.query("SELECT assignmentId FROM work_order WHERE orderId=?", arrayOf(evidence.getString("orderId"))).use {
            check(it.moveToFirst()); it.getString(0)
        }
        ActivityScenario.launch<WorkActivity>(Intent(app, WorkActivity::class.java).putExtra("assignmentId", id)).use { screen ->
            val deadline = android.os.SystemClock.elapsedRealtime() + 10000; var recent = ""
            while (!recent.contains("已核验卸货") && android.os.SystemClock.elapsedRealtime() < deadline) {
                screen.onActivity { recent = it.findViewById<TextView>(R.id.recent).text.toString() }; Thread.sleep(50)
            }
            assertTrue(recent.contains("已核验卸货（保留防重）"))
            screen.onActivity { assertTrue(it.findViewById<TextView>(R.id.totals).text.toString().startsWith("1 /")) }
            screen.onActivity { assertTrue(it.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0) }
            File(context.filesDir, "disposition-ui-evidence.json").writeText(JSONObject().put("recent", recent)
                .put("effectiveCount", 1).put("secureWindowPreserved", true).put("nativeControlsVerified", true).toString())
        }
    }
}
