package com.acrylic.outbound.pda.ui

import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.databinding.ActivityIndependentWorkBinding
import com.acrylic.outbound.pda.independent.IndependentWorkUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class IndependentWorkDisplayTest {
    @Test fun soundFailureIsVisibleAlongsideDurableSaveMessage() {
        val binding = binding()

        IndependentWorkDisplay(binding).render(
            IndependentWorkUi(message = "已保存到本机；独立采集待主管对账，不得放行。"),
            scanning = true,
            soundError = "声音未就绪，请以页面保存状态为准"
        )

        assertTrue(binding.workMessage.text.contains("已保存到本机"))
        assertTrue(binding.workMessage.text.contains("声音未就绪，请以页面保存状态为准"))
    }

    @Test fun emptySoundStateDoesNotAddBlankMessageLine() {
        val binding = binding()

        IndependentWorkDisplay(binding).render(IndependentWorkUi(message = "待核对"), scanning = false)

        assertFalse(binding.workMessage.text.contains("\n\n"))
    }

    @Test fun frequentQueueCountIsVisibleOutsidePoliteOperationFeedback() {
        val binding = binding()
        val display = IndependentWorkDisplay(binding)
        val operationFeedback = "独立采集始终待主管对账。"
        display.render(IndependentWorkUi(message = operationFeedback), scanning = true)
        val initialOperationFeedback = binding.workMessage.text.toString()

        (1..64).forEach { count ->
            display.render(IndependentWorkUi(queued = count, message = operationFeedback), scanning = true)
        }

        assertEquals("已进入队列 64 项；须见已保存结果后核对。", binding.workQueue.text.toString())
        assertEquals(View.VISIBLE, binding.workQueue.visibility)
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_NONE, binding.workQueue.accessibilityLiveRegion)
        assertEquals(initialOperationFeedback, binding.workMessage.text.toString())
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, binding.workMessage.accessibilityLiveRegion)
    }

    private fun binding() = ActivityIndependentWorkBinding.inflate(
        LayoutInflater.from(ApplicationProvider.getApplicationContext())
    )
}
