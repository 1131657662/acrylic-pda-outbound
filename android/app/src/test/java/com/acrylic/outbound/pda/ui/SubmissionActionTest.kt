package com.acrylic.outbound.pda.ui

import android.view.*
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.databinding.ActivitySubmissionBinding
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class SubmissionActionTest {
    private fun binding() = ActivitySubmissionBinding.inflate(LayoutInflater.from(ApplicationProvider.getApplicationContext<android.content.Context>()))
    @Test fun formalStatesExposeOnlyTheirNextAction() {
        val b = binding()
        listOf(null, "FROZEN", "ACTION_REQUIRED", "RESULT_UNKNOWN", "REGISTERED", "RECEIVING", "REVIEW_REQUIRED", "SYNCED", "REJECTED", "ABORTED").forEach { state ->
            SubmissionNextAction.render(b, state)
            val visible = listOf(b.freeze, b.upload, b.refresh).count { it.visibility == View.VISIBLE }
            assertEquals(if (state in setOf("SYNCED", "REJECTED", "ABORTED")) 0 else 1, visible)
            if (state in setOf("RESULT_UNKNOWN", "REGISTERED", "RECEIVING")) {
                assertTrue(b.refresh.text.contains("先查询原批次")); assertTrue(b.nextStep.text.contains("不新建批次"))
            }
        }
    }
    @Test fun temporaryStatesRetainIndependentConfirmationBoundary() {
        val b = binding()
        TemporarySubmissionNextAction.render(b, "REVIEW_REQUIRED")
        assertEquals(View.GONE, b.upload.visibility); assertEquals(View.VISIBLE, b.refresh.visibility)
        assertTrue(b.nextStep.text.contains("不代表独立确认"))
        TemporarySubmissionNextAction.render(b, "SYNCED")
        assertTrue(b.nextStep.text.contains("归档入口尚未开放"))
    }
}
