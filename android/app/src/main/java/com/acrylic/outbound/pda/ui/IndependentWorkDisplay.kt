package com.acrylic.outbound.pda.ui

import android.text.TextUtils
import android.widget.TextView
import com.acrylic.outbound.pda.databinding.ActivityIndependentWorkBinding
import com.acrylic.outbound.pda.independent.*
import java.math.BigDecimal

internal class IndependentWorkDisplay(private val binding: ActivityIndependentWorkBinding) {
    fun render(ui: IndependentWorkUi, scanning: Boolean, soundError: String = "") {
        val task = ui.task
        val sealed = task?.state?.state == "SEALED"
        binding.workSummary.show(task?.let { "${it.task.vehicleNo} · 计划 ${it.task.plannedTotal} 包\n有效 ${it.state.actualCount} 包 · ${kg(it.state.weightGrams)} kg" } ?: "正在读取原独立任务")
        binding.workBlocked.show(ui.blocked)
        binding.workMessage.show(listOf(ui.message,
            if (ui.queued > 0) "已进入队列 ${ui.queued} 项；须见已保存结果后核对。" else "", soundError)
            .filter { it.isNotBlank() }.joinToString("\n"))
        buttons(ui, scanning, sealed)
        val candidate = ui.candidate
        binding.workCandidate.show(candidate?.let { "${it.capture.barcode} · ${status(it.processing.state)}\n${it.processing.error}\n原输入序 ${it.capture.sequence} · ${if (it.capture.inputSource == "MANUAL") "手输" else "扫码"}" }.orEmpty())
        binding.workLines.show(task?.lines?.joinToString("\n") { "${it.deliveryCode} · ${it.model} · ${it.name}\n固定计划 ${it.plannedCount} 包" }.orEmpty())
        binding.workSelected.show(ui.selected?.let { row(it) } ?: "未选择原已扫包。")
        binding.workRows.show(ui.rows.joinToString("\n\n") { row(it) }.ifEmpty { "本页尚无原保存明细。" })
        binding.workOriginal.show(task?.let { "${if (sealed) "已结束封存，仍待主管对账\n" else ""}原任务 ${it.task.id}\n原资料 ${it.task.importId}\n原资料摘要 ${it.task.skuSha256}\n采集原因 ${it.task.reason}" }.orEmpty())
    }
    private fun buttons(ui: IndependentWorkUi, scanning: Boolean, sealed: Boolean) {
        binding.workManual.isEnabled = ui.ready; binding.workValidate.isEnabled = ui.ready
        binding.workConfirm.isEnabled = ui.candidate?.processing?.state == "WAITING_CONFIRM" && scanning && ui.queued == 0 && ui.blocked.isBlank()
        binding.workAcknowledge.isEnabled = ui.candidate?.processing?.state in setOf("INVALID", "BUSINESS_REJECTED") && ui.queued == 0 && ui.blocked.isBlank()
        binding.workDiscard.isEnabled = ui.candidate?.capture?.inputSource == "MANUAL" && ui.candidate.processing.state in setOf("RECEIVED", "WAITING_CONFIRM") && ui.queued == 0 && ui.blocked.isBlank()
        binding.workPause.show(if (scanning) "暂停扫码，保留已接收输入" else "恢复扫码")
        binding.workAuthorize.isEnabled = ui.queued == 0 && !sealed
        binding.workPause.isEnabled = !sealed
        binding.workFinish.isEnabled = !sealed && !scanning && ui.queued == 0 && ui.candidate == null && ui.blocked.isBlank()
        binding.workFinishReason.isEnabled = !sealed; binding.workFinishConfirmed.isEnabled = !sealed
        binding.workVoid.isEnabled = !sealed && !scanning && ui.queued == 0 && ui.candidate == null && ui.selected?.status == "ACTIVE"
        binding.workOlder.isEnabled = ui.rows.size == 50 && ui.queued == 0
    }
    private fun row(value: IndependentScanView) = "原序 ${value.scan.sequence} · ${if (value.status == "ACTIVE") "有效" else "已撤销"} · ${value.scan.barcode}\n${value.scan.deliveryCode} · ${value.scan.model} · ${value.scan.name}\n原重量 ${value.scan.rawWeight} kg / ${value.scan.weightGrams} 克"
    private fun status(value: String) = when (value) {
        "RECEIVED" -> "已接收，待校验"
        "WAITING_CONFIRM" -> "手输校验通过，待确认保存"
        "CONFIRM_REQUESTED" -> "已确认，待保存"
        "INVALID", "BUSINESS_REJECTED" -> "本包未保存，待核对"
        else -> "原输入状态待核对"
    }
    private fun kg(grams: Long) = BigDecimal.valueOf(grams, 3).stripTrailingZeros().toPlainString()
    private fun TextView.show(value: String) { if (!TextUtils.equals(text, value)) text = value }
}
