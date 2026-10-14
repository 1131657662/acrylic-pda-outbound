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
        binding.source.text = "正式单 · 未获最终确认不得放行"
        binding.totals.text = order?.let { "已保存 ${it.actualCount} / 计划 ${it.plannedTotal} 包\n${kg(it.weightGrams)} kg" } ?: "暂无本地作业"
        binding.message.text = listOf(state.blocked.ifEmpty { state.message },
            if (state.queued > 0) "待落盘 ${state.queued} · 请暂缓扫码" else if (state.pending > 1) "待处理 ${state.pending} · 请暂缓扫码" else "",
            soundError).filter { it.isNotEmpty() }.joinToString("\n")
        val stopped = when (order?.state) {
            "FROZEN" -> "本单已冻结，不能继续扫码或撤销；请进入结单页核对云端回执"
            "RECALL_PENDING", "RECALLED" -> "本单已收到召回并停止扫码，请进入召回页核对恢复证据及停止证明"
            else -> ""
        }
        if (stopped.isNotEmpty()) binding.message.text = listOf(state.blocked, stopped).filter { it.isNotEmpty() }.joinToString("\n")
        binding.closeOrder.isEnabled = order?.state !in setOf("RECALL_PENDING", "RECALLED")
        binding.pauseScan.text = if (state.ready) "暂缓扫码" else "恢复本单扫码"
        binding.pauseScan.isEnabled = order?.state == "SCANNING" && state.blocked.isBlank()
        binding.recentSaved.text = state.recent.firstOrNull { it.status == "ACTIVE" && it.captureId !in state.dispositions }?.let {
            "最近本机已保存：${it.orderScanSeq}. ${it.barcode} · ${kg(it.weightGrams)} kg" } ?: "尚无本机有效已保存包"
        val visibleLines = state.lines.take(3)
        binding.products.text = visibleLines.joinToString("\n") { "${it.deliveryCode}  ${it.actualCount}/${it.plannedCount} 包 · ${kg(it.weightGrams)} kg" } +
            if (state.lines.size > visibleLines.size) "\n还有 ${state.lines.size - visibleLines.size} 个产品，结单前请打开明细核对" else ""
        binding.recent.text = "最近记录（扫码序号）\n" + state.recent.take(5).joinToString("\n") {
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
            else -> "处理当前输入"
        }
        binding.confirm.visibility = if (c != null || state.blocked.isNotEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        binding.confirm.isEnabled = c?.state in setOf("INVALID", "BUSINESS_REJECTED") || state.blocked.isNotEmpty() || (state.ready && c?.state == "WAITING_CONFIRM")
        binding.validate.isEnabled = state.ready
        binding.voidLast.isEnabled = state.ready && state.pending == 0 && state.queued == 0 && c == null && state.recent.any { it.status == "ACTIVE" && it.captureId !in state.dispositions }
        binding.adjustPackages.isEnabled = state.ready && state.pending == 0 && state.queued == 0 && c == null
    }
    companion object { fun kg(grams: Long): String = BigDecimal.valueOf(grams, 3).stripTrailingZeros().toPlainString() }
}
