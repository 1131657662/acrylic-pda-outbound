package com.acrylic.outbound.pda.ui

import android.text.TextUtils
import android.widget.TextView
import androidx.core.view.isVisible
import com.acrylic.outbound.pda.databinding.ActivityIndependentWorkBinding
import com.acrylic.outbound.pda.independent.*
import java.math.BigDecimal

internal class IndependentWorkDisplay(private val binding: ActivityIndependentWorkBinding) {
    fun render(ui: IndependentWorkUi, scanning: Boolean, soundError: String = "") {
        val task = ui.task
        val sealed = task?.state?.state == "SEALED"
        binding.workVehicle.show(task?.task?.vehicleNo.orEmpty())
        binding.workVehicle.contentDescription = task?.let { "车号 ${it.task.vehicleNo}" }
        binding.workSummary.show(task?.let(::summary) ?: "正在读取原独立任务")
        saved(ui)
        feedback(ui, soundError)
        buttons(ui, scanning, sealed)
        candidate(ui.candidate)
        binding.workLines.show(task?.let { "有效重量 ${kg(it.state.weightGrams)} kg\n" + it.lines.joinToString("\n") { line -> "${line.deliveryCode} · ${line.model} · ${line.name}\n固定计划 ${line.plannedCount} 包" } }.orEmpty())
        binding.workSelected.show(ui.selected?.let { row(it) } ?: "未选择原已扫包。")
        binding.workRows.show(ui.rows.joinToString("\n\n") { row(it) }.ifEmpty { "本页尚无原保存明细。" })
        binding.workOriginal.show(task?.let { "${if (sealed) "已结束封存，仍待主管对账\n" else ""}原任务 ${it.task.id}\n原资料 ${it.task.importId}\n原资料摘要 ${it.task.skuSha256}\n采集原因 ${it.task.reason}" }.orEmpty())
    }
    private fun saved(ui: IndependentWorkUi) {
        val task = ui.task
        val saved = ui.latestSaved?.takeIf { it.scan.taskId == task?.task?.id }
        binding.workRecent.show(saved?.let(::recent).orEmpty())
        binding.workRecent.isVisible = binding.workRecent.text.isNotBlank()
        binding.workRecentDetails.show(saved?.let { "最近保存原件\n${row(it)}" }.orEmpty())
        binding.workRecentDetails.isVisible = saved != null
    }
    private fun feedback(ui: IndependentWorkUi, soundError: String) {
        binding.workBlocked.show(ui.blocked)
        binding.workBlocked.isVisible = ui.blocked.isNotBlank()
        binding.workQueue.show(if (ui.queued > 0) "已进入队列 ${ui.queued} 项；须见已保存结果后核对。" else "")
        binding.workQueue.isVisible = ui.queued > 0
        val message = when (ui.message) {
            "独立采集始终待主管对账。" -> ""
            "已保存到本机；独立采集待主管对账，不得放行。" ->
                if (ui.latestSaved?.scan?.taskId == ui.task?.task?.id && ui.latestSaved?.status == "ACTIVE") "" else "已保存到本机"
            else -> ui.message
        }
        binding.workMessage.show(listOf(message, soundError)
            .filter { it.isNotBlank() }.joinToString("\n"))
        binding.workMessage.isVisible = binding.workMessage.text.isNotBlank()
    }
    private fun candidate(candidate: IndependentCaptureView?) {
        binding.workCandidate.isVisible = candidate != null
        binding.workCandidate.show(candidate?.let { listOf("${status(it.processing.state)}\n${it.capture.barcode}",
            it.processing.error)
            .filter { line -> line.isNotBlank() }.joinToString("\n") }.orEmpty())
        binding.workCandidateDetails.show(candidate?.let { "原输入序 ${it.capture.sequence} · ${if (it.capture.inputSource == "MANUAL") "手输" else "扫码"}\n${it.capture.barcode}" }.orEmpty())
        binding.workCandidateDetails.isVisible = candidate != null
    }
    private fun buttons(ui: IndependentWorkUi, scanning: Boolean, sealed: Boolean) {
        binding.workManual.isEnabled = ui.ready && !sealed; binding.workValidate.isEnabled = ui.ready && !sealed
        binding.workConfirm.isVisible = ui.candidate?.processing?.state == "WAITING_CONFIRM"
        binding.workAcknowledge.isVisible = ui.candidate?.processing?.state in setOf("INVALID", "BUSINESS_REJECTED")
        binding.workDiscard.isVisible = ui.candidate?.capture?.inputSource == "MANUAL" &&
            ui.candidate.processing.state in setOf("RECEIVED", "WAITING_CONFIRM")
        binding.workConfirm.isEnabled = !sealed && ui.candidate?.processing?.state == "WAITING_CONFIRM" && scanning && ui.queued == 0 && ui.blocked.isBlank()
        binding.workAcknowledge.isEnabled = !sealed && ui.candidate?.processing?.state in setOf("INVALID", "BUSINESS_REJECTED") && ui.queued == 0 && ui.blocked.isBlank()
        binding.workDiscard.isEnabled = !sealed && ui.candidate?.capture?.inputSource == "MANUAL" && ui.candidate.processing.state in setOf("RECEIVED", "WAITING_CONFIRM") && ui.queued == 0 && ui.blocked.isBlank()
        binding.workPause.show(if (scanning) "暂停扫码" else "恢复扫码")
        binding.workAuthorize.isEnabled = ui.queued == 0 && !sealed
        binding.workPause.isEnabled = !sealed
        binding.workFinish.isEnabled = !sealed && !scanning && ui.queued == 0 && ui.candidate == null && ui.blocked.isBlank()
        binding.workFinishReason.isEnabled = !sealed; binding.workFinishConfirmed.isEnabled = !sealed
        binding.workVoid.isEnabled = !sealed && !scanning && ui.queued == 0 && ui.candidate == null && ui.selected?.status == "ACTIVE"
        binding.workOlder.isEnabled = ui.rows.size == 50 && ui.queued == 0
    }
    private fun row(value: IndependentScanView) = "原序 ${value.scan.sequence} · ${if (value.status == "ACTIVE") "有效" else "已撤销"} · ${value.scan.barcode}\n${value.scan.deliveryCode} · ${value.scan.model} · ${value.scan.name}\n原重量 ${value.scan.rawWeight} kg / ${value.scan.weightGrams} 克"
    private fun summary(value: IndependentTaskView): String {
        val count = value.state.actualCount; val plan = value.task.plannedTotal
        val remaining = if (count <= plan) "剩余${plan - count}包" else "超计划${count - plan}包"
        return "计划${plan}包\n有效${count}包·$remaining"
    }
    private fun recent(value: IndependentScanView): String {
        val result = if (value.status == "ACTIVE") "已存" else "已撤销"
        return "$result ${value.scan.barcode}"
    }
    private fun status(value: String) = when (value) {
        "RECEIVED" -> "已接收，待校验"
        "WAITING_CONFIRM" -> "手输待确认保存"
        "CONFIRM_REQUESTED" -> "已确认，待保存"
        "INVALID", "BUSINESS_REJECTED" -> "本包未保存，待核对"
        else -> "原输入状态待核对"
    }
    private fun kg(grams: Long) = BigDecimal.valueOf(grams, 3).stripTrailingZeros().toPlainString()
    private fun TextView.show(value: String) { if (!TextUtils.equals(text, value)) text = value }
}
