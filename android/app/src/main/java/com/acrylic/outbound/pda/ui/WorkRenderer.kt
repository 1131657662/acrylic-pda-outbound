package com.acrylic.outbound.pda.ui

import com.acrylic.outbound.pda.databinding.ActivityWorkBinding
import com.acrylic.outbound.pda.work.WorkUi
import java.math.BigDecimal

class WorkRenderer(private val binding: ActivityWorkBinding) {
    var displayed = WorkUi()
        private set
    fun render(state: WorkUi, soundError: String) {
        displayed = state
        val order = state.order
        binding.workControls.visibility = if (order != null && order.state != "SCANNING") android.view.View.GONE else android.view.View.VISIBLE
        binding.vehicle.text = order?.vehicleNo ?: "正在打开订单"
        binding.totals.text = order?.let { "${it.actualCount} / ${it.plannedTotal} 包 · ${kg(it.weightGrams)} kg" } ?: "暂无本地作业"
        binding.message.text = listOf(state.blocked.ifEmpty { state.message },
            "待处理 ${state.pending} · 待落盘 ${state.queued}" + if (state.pending >= 16) "，请放慢扫码" else "",
            soundError).filter { it.isNotEmpty() }.joinToString("\n")
        if (order?.state == "FROZEN") binding.message.text = "本单已冻结，不能继续扫码或撤销；请进入结单页核对云端回执"
        if (order?.state in setOf("RECALL_PENDING", "RECALLED")) binding.message.text = "本单已收到召回并停止扫码，请进入召回页核对恢复证据及停止证明"
        binding.closeOrder.isEnabled = order?.state !in setOf("RECALL_PENDING", "RECALLED")
        binding.products.text = state.lines.joinToString("\n") { "${it.deliveryCode}  ${it.actualCount}/${it.plannedCount} 包 · ${kg(it.weightGrams)} kg" }
        binding.recent.text = "最近记录（扫码序号）\n" + state.recent.joinToString("\n") {
            "${it.orderScanSeq}. ${it.barcode}  ${kg(it.weightGrams)} kg" + when {
                state.dispositions[it.captureId] == "RELEASE" -> " · 已核验卸货（保留防重）"
                state.dispositions[it.captureId] == "QUARANTINE" -> " · 已核验隔离"
                it.status == "VOID" -> " · 已撤销"
                else -> ""
            } }
        val c = state.candidate
        binding.candidate.text = c?.let { "${it.context.orderScanSeq}. ${it.context.barcode}\n" + when (it.state) {
            "WAITING_CONFIRM" -> "手输校验通过，确认后保存"
            "INVALID", "BUSINESS_REJECTED" -> "本包未保存：${it.error}"
            else -> "原输入已保存，等待处理"
        } } ?: "请核对车号后扫码"
        binding.confirm.text = when {
            c?.state in setOf("INVALID", "BUSINESS_REJECTED") -> "确认处理结果（本包未保存）"
            state.blocked.isNotEmpty() -> "恢复核对原记录"
            c?.state == "WAITING_CONFIRM" -> "确认保存这包 · 回车"
            else -> "扫码通过后自动保存"
        }
        binding.confirm.isEnabled = c?.state in setOf("INVALID", "BUSINESS_REJECTED") || state.blocked.isNotEmpty() || (state.ready && c?.state == "WAITING_CONFIRM")
        binding.validate.isEnabled = state.ready
        binding.voidLast.isEnabled = state.ready && state.pending == 0 && state.queued == 0 && c == null && state.recent.any { it.status == "ACTIVE" && it.captureId !in state.dispositions }
    }
    companion object { fun kg(grams: Long): String = BigDecimal.valueOf(grams, 3).stripTrailingZeros().toPlainString() }
}
