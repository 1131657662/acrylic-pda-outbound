package com.acrylic.outbound.pda

import android.content.Intent
import android.view.View
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.independent.IndependentWorkRuntime
import com.acrylic.outbound.pda.ui.IndependentWorkActivity
import org.hamcrest.CoreMatchers.containsString
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import com.acrylic.outbound.pda.IndependentCredentialDeviceFixture as F

/** Read an already committed retained task through the real product page; never add an input. */
class IndependentWorkRetainedSavedNativeTest {
    @Test fun retainedCommittedInputAppearsAsTheSameRecentSavedRecord() {
        IndependentScanDeviceFixture.guard()
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("independentWorkNative") == "true")
        val taskId = checkNotNull(args.getString("retainedTaskId"))
        val run = checkNotNull(args.getString("readbackRun"))
        check(run.matches(Regex("readback-font-[24]")))
        val directory = File(F.app.filesDir, "independent-work-native/$run")
        check(!directory.exists() && directory.mkdirs())
        val before = original(taskId)
        val intent = Intent(F.app, IndependentWorkActivity::class.java).putExtra("taskId", taskId)
        ActivityScenario.launch<IndependentWorkActivity>(intent).use { scenario ->
            F.await { assertEquals(taskId, IndependentWorkRuntime.shared(F.app).state.value.task?.task?.id) }
            IndependentScanDeviceFixture.authorize()
            F.await {
                val state = IndependentWorkRuntime.shared(F.app).state.value
                assertEquals(1L, state.task?.state?.actualCount)
                assertEquals(before.capture.id, state.latestSaved?.scan?.captureId)
                assertEquals(0, state.queued); assertNull(state.candidate)
                onView(withId(R.id.workRecent)).check(matches(withText(containsString(before.capture.rawInput))))
            }
            F.click(R.id.workDetailsToggle)
            capture(scenario, directory)
            assertEquals(before, original(taskId))
        }
        File(directory, "result.json").writeText(JSONObject().put("taskId", taskId)
            .put("captureId", before.capture.id).put("barcode", before.capture.rawInput)
            .put("actualCount", 1).put("recentSavedVisible", true).put("newInput", false).toString(2))
    }

    private fun original(taskId: String) = F.app.database.independentScans().captures(taskId).single().also {
        assertEquals("COMMITTED", it.processing.state)
        assertEquals(1L, IndependentScanDeviceFixture.task(taskId).state.actualCount)
    }

    private fun capture(scenario: ActivityScenario<IndependentWorkActivity>, directory: File) {
        scenario.onActivity { activity ->
            val root = activity.findViewById<View>(R.id.workBack).parent.parent as ScrollView
            root.scrollTo(0, 0)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        scenario.onActivity { activity ->
            val insets = checkNotNull(ViewCompat.getRootWindowInsets(activity.window.decorView))
            assertFalse(insets.isVisible(WindowInsetsCompat.Type.ime()))
            IndependentSkuDeviceEvidence.capture(activity, directory, "saved-readback")
        }
    }
}
