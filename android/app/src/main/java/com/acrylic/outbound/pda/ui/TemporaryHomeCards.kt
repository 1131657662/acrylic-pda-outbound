package com.acrylic.outbound.pda.ui

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import com.acrylic.outbound.pda.databinding.OrderCardBinding

class TemporaryHomeCards(private val parent: LinearLayout) {
    private var previous: Pair<List<TemporaryHomeCard>, Boolean>? = null
    fun render(state: PreparationUi) {
        val content = state.temporaryLocal to state.busy
        if (content == previous) return
        previous = content; parent.removeAllViews()
        state.temporaryLocal.forEach { card ->
            val row = card.draft
            val b = OrderCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            b.title.text = "临时单 · 车号 ${row.vehicleNo.ifBlank { "待填写" }}"
            b.details.text = "本机有效已保存 ${card.savedCount} / 本单计划 ${card.plannedCount} 包\n${when (row.state) {
                "DRAFT" -> "草稿 · 尚未扫码"; "FROZEN" -> "已冻结 · 核对原批次回执"; "EVIDENCE_STOPPED" -> "已停止 · 原件保留"
                "CONTINUATION_BLOCKED" -> "续作待受控确认"; "HANDOVER_BLOCKED" -> "旧冻结原件 · 只读核对"; else -> "本机作业 · 尚未确认出库" }}"
            b.action.visibility = View.GONE; b.work.visibility = View.VISIBLE; b.work.isEnabled = !state.busy
            b.work.text = when (row.state) { "DRAFT" -> "继续编辑草稿"; "SCANNING" -> "继续扫码"; else -> "核对原单状态" }
            b.work.setOnClickListener { parent.context.startActivity(Intent(parent.context, when (row.state) {
                "DRAFT" -> TemporaryDraftActivity::class.java; "FROZEN" -> TemporarySubmissionActivity::class.java
                "EVIDENCE_STOPPED" -> LocalDraftEvidenceActivity::class.java; "CONTINUATION_BLOCKED" -> TemporaryContinuationActivity::class.java
                "HANDOVER_BLOCKED" -> TemporaryDetailsActivity::class.java; else -> TemporaryWorkActivity::class.java
            }).putExtra("clientOrderId", row.clientOrderId)) }
            parent.addView(b.root)
        }
    }
}
