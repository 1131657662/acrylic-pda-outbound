package com.acrylic.outbound.pda.ui

import com.acrylic.outbound.pda.work.WorkDetailRow

/** Human-readable state never turns receipt of an input into a successful save. */
object WorkDetailsText {
    fun adjustment(reason: String): String = when {
        reason.isBlank() -> "调整已扫码包"
        reason.startsWith("No value for") -> "调整不可用 · 本机作业资格资料不完整，请核对原身份与订单；原记录保留"
        reason.any { it in '\u4e00'..'\u9fff' } -> "调整不可用 · $reason"
        else -> "调整不可用 · 资格核对未完成，请查看原始原因并核对；原记录保留"
    }

    fun row(row: WorkDetailRow): String {
        val status = when (row.status) {
            "ACTIVE" -> "已保存"
            "RETAINED" -> "已保存原记录 · 主管核验保留，须受控处置"
            "VOID" -> "已撤销 · 保留原记录"
            "RECEIVED" -> "已接收 · 等待处理，尚未保存"
            "WAITING_CONFIRM" -> "等待确认 · 尚未保存"
            "CONFIRM_REQUESTED" -> "已请求确认 · 等待落盘，尚未保存"
            "INVALID", "BUSINESS_REJECTED" -> if (row.failureAckAt == null) "输入被拒绝 · 等待处理" else "输入被拒绝 · 已确认异常"
            "CANCELLED" -> "已取消输入 · 未保存"
            "DISCARDED" -> "已放弃手输项 · 未保存"
            else -> "状态待核对（原码：${row.status}）"
        }
        val product = row.deliveryCode?.let { "\n$it · ${row.model.orEmpty()} · ${row.name.orEmpty()}" }.orEmpty()
        val weight = row.weightGrams?.let { " · ${WorkRenderer.kg(it)} kg" }.orEmpty()
        val error = row.error.takeIf { it.isNotBlank() }?.let { "\n原原因：$it" }.orEmpty()
        return "原序号 ${row.sequence} · ${row.barcode}\n$status$weight$product$error"
    }
}
