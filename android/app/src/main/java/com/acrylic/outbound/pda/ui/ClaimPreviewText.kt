package com.acrylic.outbound.pda.ui

import org.json.JSONObject

/** Formats server preview facts; never guesses from a vehicle or local assignment. */
internal object ClaimPreviewText {
    fun render(preview: JSONObject): String {
        val number = required(preview, "orderNo"); val vehicle = required(preview, "vehicleNo")
        val warehouse = required(preview, "warehouseName")
        required(preview, "warehouseId"); required(preview, "id"); required(preview, "rowVersion")
        check(preview.getString("status") == "READY") { "订单已不在待领取状态，请刷新核对" }
        val total = required(preview, "plannedTotal").toLongOrNull()
        check(total != null && total > 0) { "领取预览计划量缺失或无效，请刷新核对" }
        val lines = preview.getJSONArray("lines")
        check(lines.length() > 0) { "领取预览产品缺失，请刷新核对" }
        var sum = 0L
        val products = (0 until lines.length()).map { index ->
            val line = lines.getJSONObject(index)
            val count = line.getLong("plannedCount")
            check(count > 0) { "领取预览产品计划量无效，请刷新核对" }
            sum = Math.addExact(sum, count)
            "${required(line, "deliveryCode")} · ${required(line, "model")} · ${required(line, "name")} · $count 包"
        }
        check(sum == total) { "领取预览产品与总计划量不一致，请刷新核对" }
        val more = if (products.size > 5) "\n另有 ${products.size - 5} 类产品" else ""
        return "订单号 $number\n车号 $vehicle\n仓库 $warehouse\n计划 $total 包\n产品摘要\n" +
            products.take(5).joinToString("\n") + more + "\n领取后由本机独占；下载失败或到期仍保留占用。"
    }

    private fun required(value: JSONObject, field: String): String =
        (value.opt(field) as? String)?.takeIf { it.isNotBlank() }
            ?: error("领取预览信息不完整（$field），请刷新核对")
}
