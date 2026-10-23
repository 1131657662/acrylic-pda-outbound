package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.display.DisplayTime
import org.json.JSONObject

/** Human-readable hints only; the original target and read grant remain authoritative. */
internal object RescueOrderLabel {
    fun format(target: RescueTarget, recordedAt: String, vehicleNo: String, orderNo: String, planJson: String): String {
        val formal = if (target.source == "FORMAL") runCatching {
            JSONObject(planJson).optJSONObject("order")?.optJSONObject("order")
        }.getOrNull() else null
        val type = when (target.source) { "FORMAL" -> "正式"; "TEMP" -> "临时"; else -> "独立 · 待主管对账" }
        val time = if (target.source == "INDEPENDENT") DisplayTime.formatEpochMillis(recordedAt.toLongOrNull())
            else DisplayTime.formatDateTime(recordedAt)
        val timeKind = if (target.source == "FORMAL") "资料保存" else "本机建单"
        val number = if (target.source == "FORMAL") formal?.optString("orderNo").orEmpty() else orderNo
        val business = if (target.source == "INDEPENDENT") "" else "\n订单号 ${number.ifBlank { "未记录" }}"
        return "$type · $timeKind $time\n车号 ${vehicleNo.ifBlank { "未记录" }}$business\n记录编号 ${target.localOrderId}"
    }
}
