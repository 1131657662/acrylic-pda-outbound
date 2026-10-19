package com.acrylic.outbound.pda.ui

import android.view.LayoutInflater
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.databinding.ActivityIndependentWorkBinding
import com.acrylic.outbound.pda.independent.IndependentWorkUi
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

    private fun binding() = ActivityIndependentWorkBinding.inflate(
        LayoutInflater.from(ApplicationProvider.getApplicationContext())
    )
}
