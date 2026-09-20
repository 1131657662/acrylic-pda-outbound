package com.acrylic.outbound.pda.ui

import android.view.View
import com.acrylic.outbound.pda.databinding.ActivityWorkBinding
import com.acrylic.outbound.pda.temporary.TemporaryWorkUi

class TemporaryWorkRenderer(private val binding: ActivityWorkBinding) {
    var displayed = TemporaryWorkUi()
        private set
    fun render(state: TemporaryWorkUi, soundError: String) {
        displayed = state
        binding.vehicle.text = "临时单 · ${state.draft?.vehicleNo.orEmpty()}"
        binding.totals.text = "${state.total.count} / ${state.lines.sumOf { it.plannedCount }} 包 · ${WorkRenderer.kg(state.total.grams)} kg"
        binding.message.text = listOf("未获云端最终确认，不得放行", state.blocked.ifBlank { state.message },
            "待落盘 ${state.queued}", soundError).filter { it.isNotBlank() }.joinToString("\n")
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
    private fun controls(state: TemporaryWorkUi) {
        binding.recall.visibility = View.GONE
        binding.closeOrder.text = "尚未开放上传 · 不得放行"; binding.closeOrder.isEnabled = false
        binding.validate.isEnabled = state.ready
        binding.voidLast.isEnabled = state.ready && state.queued == 0 && state.candidate == null && state.recent.any { it.status == "ACTIVE" }
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
