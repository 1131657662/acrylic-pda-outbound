package com.acrylic.outbound.pda.ui

import android.view.View
import com.acrylic.outbound.pda.databinding.ActivityWorkBinding
import com.acrylic.outbound.pda.temporary.TemporaryWorkUi

class TemporaryWorkRenderer(private val binding: ActivityWorkBinding) {
    var displayed = TemporaryWorkUi()
        private set
    fun render(state: TemporaryWorkUi, soundError: String) {
        displayed = state
        binding.workControls.visibility = if (state.draft?.state == "SCANNING" ||
            state.candidate != null || state.blocked.isNotBlank()) View.VISIBLE else View.GONE
        binding.vehicle.text = state.draft?.vehicleNo?.ifBlank { "临时作业" } ?: "临时作业"
        binding.source.text = "临时单 · 未获最终确认不得放行"
        binding.totals.text = "已保存 ${state.total.count} / 计划 ${state.lines.sumOf { it.plannedCount }} 包\n${WorkRenderer.kg(state.total.grams)} kg"
        binding.message.text = (listOf("临时单未获最终确认，不得放行", primaryMessage(state)) +
            processingStatus(state) + soundError).filter { it.isNotBlank() }.joinToString("\n")
        binding.pauseScan.text = if (state.ready) "暂缓扫码" else "恢复本单扫码"
        binding.pauseScan.isEnabled = state.draft?.state == "SCANNING" && state.blocked.isBlank()
        binding.recentSaved.text = state.recent.firstOrNull { it.status == "ACTIVE" }?.let {
            "最近本机已保存：${it.orderScanSeq}. ${it.barcode} · ${WorkRenderer.kg(it.weightGrams)} kg" } ?: "尚无本机有效已保存包"
        val counts = state.products.associateBy { it.deliveryCode }
        binding.products.text = state.lines.take(3).joinToString("\n") { "${it.deliveryCode} · ${counts[it.deliveryCode]?.count ?: 0}/${it.plannedCount} 包" } +
            if (state.lines.size > 3) "\n其余产品请打开明细核对" else ""
        binding.recent.text = "最近记录（原扫码序号）\n" + state.recent.joinToString("\n") {
            "${it.orderScanSeq}. ${it.barcode} · ${WorkRenderer.kg(it.weightGrams)} kg${if (it.status == "VOID") " · 已撤销" else ""}"
        }
        val candidate = state.candidate
        binding.candidate.text = candidate?.let { "${it.orderScanSeq}. ${it.barcode}\n" + when (it.state) {
            "WAITING_CONFIRM" -> "手输校验通过，确认后保存"
            "INVALID", "BUSINESS_REJECTED" -> "本包未保存：${it.error}"
            else -> "原输入已保存，等待处理"
        } } ?: "请核对车号后扫码"
        controls(state)
    }
    private fun primaryMessage(state: TemporaryWorkUi): String = when {
        state.blocked.isNotEmpty() -> state.blocked
        state.candidate != null -> "待确认输入 1 · 请完成确认或处理"
        else -> state.message
    }
    private fun processingStatus(state: TemporaryWorkUi): List<String> = listOf(
        if (state.queued > 0) "接收队列待落盘 ${state.queued}，请暂缓扫码" else "",
        if (state.blocked.isNotEmpty() && state.candidate != null) "待确认输入 1 · 请完成确认或处理" else ""
    )
    private fun controls(state: TemporaryWorkUi) {
        binding.recall.visibility = View.VISIBLE
        binding.recall.text = if (state.draft?.state == "EVIDENCE_STOPPED") "查看原件保全 / 补传" else "停止并保全本单原件"
        binding.recall.isEnabled = state.draft?.state in setOf("SCANNING", "EVIDENCE_STOPPED")
        binding.closeOrder.text = "核对并结单上传 · 不得放行"
        binding.closeOrder.isEnabled = state.draft?.state == "SCANNING" && state.queued == 0 && state.candidate == null && state.total.count > 0
        binding.validate.isEnabled = state.ready
        binding.voidLast.isEnabled = state.ready && state.queued == 0 && state.candidate == null && state.recent.any { it.status == "ACTIVE" }
        binding.adjustPackages.isEnabled = state.ready && state.queued == 0 && state.candidate == null
        binding.confirm.text = when {
            state.candidate?.state in setOf("INVALID", "BUSINESS_REJECTED") -> "确认处理结果（本包未保存）"
            state.blocked.isNotBlank() -> "恢复核对原临时单"
            state.candidate?.state == "WAITING_CONFIRM" -> "确认保存这包 · 回车"
            else -> "处理当前输入"
        }
        binding.confirm.visibility = if (state.candidate != null || state.blocked.isNotBlank()) View.VISIBLE else View.GONE
        binding.confirm.isEnabled = state.queued == 0
    }
}
