package com.acrylic.m71demo.ui

import android.graphics.Color
import com.acrylic.m71demo.databinding.ActivityMainBinding
import com.acrylic.m71demo.domain.Weight

class ScreenRenderer(private val view: ActivityMainBinding) {
    var displayedCapture: String? = null
        private set

    fun render(state: DemoUiState) {
        displayedCapture = state.candidate?.id
        view.orderTitle.text = state.order?.let { "${it.vehicle} · ${it.state} · 测试" } ?: "正在加载离线样本"
        view.barcode.text = state.candidate?.context?.barcode ?: "等待扫描"
        view.candidateStatus.text = listOf(state.candidateDetail, status(state)).filter { it.isNotBlank() }.joinToString("\n")
        view.candidateStatus.setTextColor(Color.parseColor(if (isError(state)) "#A32720" else "#145B54"))
        view.total.text = "${state.order?.actualCount ?: 0} / ${state.lines.sumOf { it.plannedCount.toLong() }} 包"
        view.lines.text = state.lines.take(3).joinToString("\n") { "${it.deliveryCode}  ${it.actualCount}/${it.plannedCount}   ${Weight.kg(it.weightGrams)} kg" }
        view.queue.text = "待处理 ${state.pending} · 入口 ${state.queued} · ${Weight.kg(state.order?.weightGrams ?: 0)} kg"
        val manualWaiting = state.candidate?.let { it.state == "WAITING_CONFIRM" && it.context.confirmationPolicy == "MANUAL" } == true
        val feedback = if (manualWaiting && !state.blocked) "本包尚未保存，请回车或点击确认保存" else state.message
        view.message.text = listOf(feedback, state.soundError).filter { it.isNotBlank() }.joinToString("\n")
        view.message.setTextColor(Color.parseColor(if (state.blocked) "#A32720" else "#705017"))
        view.recent.text = state.recent.take(3).joinToString("\n") { "#${it.context.orderScanSeq}  ${it.context.barcode}  ${if (it.status == "VOID") "已撤销" else "已保存"}" }
        val actionable = state.candidate?.let { it.state in setOf("INVALID", "BUSINESS_REJECTED") ||
            (it.state == "WAITING_CONFIRM" && it.context.confirmationPolicy == "MANUAL") } == true
        view.confirmButton.visibility = if (actionable) android.view.View.VISIBLE else android.view.View.GONE
        view.confirmButton.text = if (isError(state)) "确认错误并恢复" else "确认保存（等同回车）"
        view.confirmButton.isEnabled = !state.blocked
    }

    private fun status(state: DemoUiState): String = when (val c = state.candidate) {
        null -> "扫描校验通过后自动保存"
        else -> when (c.state) {
            "WAITING_CONFIRM" -> if (c.context.confirmationPolicy == "MANUAL") "手输/旧候选 · 回车确认保存" else "校验通过 · 等待自动保存"
            "INVALID", "BUSINESS_REJECTED" -> ErrorText.describe(c.error) + "\n回车确认处理结果"
            "CONFIRM_REQUESTED" -> "保存意图已登记，等待保存"
            "STORAGE_ERROR" -> "存储故障，辅助菜单恢复；禁止清除"
            else -> "输入已接收，校验中"
        }
    }

    private fun isError(state: DemoUiState) = state.candidate?.state in setOf("INVALID", "BUSINESS_REJECTED", "STORAGE_ERROR")
}
