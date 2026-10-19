package com.acrylic.outbound.pda.ui

import android.view.LayoutInflater
import androidx.core.widget.doAfterTextChanged
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.databinding.ActivityIndependentWorkBinding
import com.acrylic.outbound.pda.independent.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class IndependentWorkRenderTest {
    @Test fun queueCountDoesNotRewriteOperationFeedbackOrSavedDetails() {
        val binding = binding()
        val display = IndependentWorkDisplay(binding)
        val ui = ui()
        display.render(ui, scanning = true)
        val changes = changes(binding)

        (1..64).forEach { display.render(ui.copy(queued = it), scanning = true) }

        assertEquals(mapOf(binding.workQueue.id to 64), changes)
        assertTrue(binding.workQueue.text.contains("已进入队列 64 项"))
        assertFalse(binding.workMessage.text.contains("已进入队列"))
        assertTrue(binding.workRows.text.contains("原序 1"))
    }

    @Test fun durableSaveUpdatesCountWeightAndLatestRows() {
        val binding = binding()
        val display = IndependentWorkDisplay(binding)
        val ui = ui()
        display.render(ui.copy(queued = 1), scanning = true)
        val changes = changes(binding)
        val savedTask = ui.task!!.copy(state = ui.task.state.copy(actualCount = 2, weightGrams = 2500))

        display.render(ui.copy(task = savedTask, rows = listOf(row(2), row(1)),
            message = "已保存到本机；独立采集待主管对账，不得放行。"), scanning = true)

        assertEquals(setOf(binding.workSummary.id, binding.workMessage.id, binding.workQueue.id,
            binding.workRows.id, binding.workLines.id), changes.keys)
        assertTrue(binding.workSummary.text.contains("有效2包"))
        assertTrue(binding.workSummary.text.contains("剩余98包"))
        assertTrue(binding.workLines.text.contains("有效重量 2.5 kg"))
        assertTrue(binding.workRows.text.startsWith("原序 2"))
        assertEquals(android.view.View.GONE, binding.workQueue.visibility)
        assertFalse(binding.workMessage.text.contains("已进入队列"))
    }

    @Test fun pagingReplacesRowsWithoutRewritingPlanOrOriginalTask() {
        val binding = binding()
        val display = IndependentWorkDisplay(binding)
        val ui = ui()
        display.render(ui, scanning = false)
        val changes = changes(binding)

        display.render(ui.copy(after = 50, rows = listOf(row(51))), scanning = false)

        assertEquals(mapOf(binding.workRows.id to 1), changes)
        assertTrue(binding.workRows.text.startsWith("原序 51"))
        assertFalse(binding.workRows.text.contains("原序 1 ·"))
    }

    private fun changes(binding: ActivityIndependentWorkBinding): MutableMap<Int, Int> {
        val changes = mutableMapOf<Int, Int>()
        listOf(binding.workSummary, binding.workBlocked, binding.workMessage, binding.workQueue, binding.workCandidate, binding.workRecent,
            binding.workLines, binding.workSelected, binding.workRows, binding.workOriginal, binding.workPause)
            .forEach { view -> view.doAfterTextChanged { changes[view.id] = changes.getOrDefault(view.id, 0) + 1 } }
        return changes
    }

    private fun binding() = ActivityIndependentWorkBinding.inflate(
        LayoutInflater.from(ApplicationProvider.getApplicationContext())
    )

    private fun ui(): IndependentWorkUi {
        val task = IndependentTask(id = "task", importId = "import", skuSha256 = "synthetic-sha",
            credentialId = "credential", signedCredential = "", trustedKeyJson = "", timeAnchorJson = "",
            localSessionId = "session", operatorId = "operator", deviceId = "device", warehouseId = "warehouse",
            recoveryEpoch = "epoch", vehicleNo = "合成车号", reason = "测试独立采集", plannedTotal = 100,
            commandSha256 = "synthetic-command", createdWall = 1, createdElapsed = 1, createdBoot = 1)
        return IndependentWorkUi(task = IndependentTaskView(task,
            listOf(IndependentTaskLine("task", "003", "型号", "合成品名", 100)),
            IndependentTaskState("task", actualCount = 1, weightGrams = 1250), emptyList()),
            rows = listOf(row(1)), ready = true)
    }

    private fun row(sequence: Long) = IndependentScanView(IndependentScan(
        captureId = "capture-$sequence", taskId = "task", sequence = sequence, barcode = "003A$sequence",
        deliveryCode = "003", model = "型号", name = "合成品名", rawWeight = "1.250", weightGrams = 1250,
        skuLineNo = 1, skuRawText = "synthetic", savedTrustedAt = "synthetic-time"
    ), "ACTIVE")
}
