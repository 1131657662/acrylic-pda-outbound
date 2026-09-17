package com.acrylic.outbound.pda.preparation

import com.acrylic.outbound.pda.data.PreparedBale
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.UUID

object InventoryDecoder {
    fun decode(assignment: String, inventory: JSONArray, states: JSONArray): List<PreparedBale> {
        check(inventory.length() == states.length() && inventory.length() in 1..100_000) { "库存与状态行数不一致" }
        val codes = HashSet<String>(); val ids = HashSet<String>()
        return (0 until inventory.length()).map { index ->
            val row = inventory.getJSONObject(index); val state = states.getJSONObject(index)
            val barcode = row.getString("barcode"); val code = row.getString("deliveryCode")
            check(barcode.matches(Regex("[0-9A-Z]{11}")) && code.matches(Regex("[0-9]{3}")) && barcode.startsWith(code)) { "条码或产品代码无效" }
            check(state.getString("barcode") == barcode && codes.add(barcode)) { "条码重复或状态错位" }
            val id = UUID.fromString(state.getString("baleId")).toString(); check(ids.add(id)) { "库存身份重复" }
            val grams = row.getString("weightGrams").toLong(); val raw = row.getString("rawWeight")
            check(grams > 0 && raw.matches(Regex("[0-9]+(\\.[0-9]{1,3})?")) && BigDecimal(raw).movePointRight(3).longValueExact() == grams) { "重量数据无效" }
            val cycle = state.getString("dispatchCycle").toLong(); check(cycle > 0)
            check(state.get("available") is Boolean)
            PreparedBale(assignment, barcode, id, code, row.getString("model"), row.getString("name"), raw, grams, cycle, state.getBoolean("available"))
        }
    }

    fun validatePlan(plan: JSONObject, claims: JSONObject, bales: List<PreparedBale>) {
        val order = plan.getJSONObject("order"); val header = order.getJSONObject("order"); val detail = order.getJSONObject("plan")
        check(header.getString("id") == claims.getString("orderId") && detail.getInt("revision") == claims.getInt("revision")) { "计划订单身份错误" }
        check(detail.getString("inventorySnapshotId") == claims.getString("inventorySnapshotId"))
        val policy = plan.getJSONObject("policy")
        check(policy.getString("version") == claims.getString("policyVersion") && policy.getBoolean("autoDeductInventory") == claims.getBoolean("autoDeductInventory"))
        check(policy.getInt("offlineHours") == 48 && policy.getString("concurrencyMode") == "PHYSICAL_SEPARATION")
        val lines = order.getJSONArray("lines"); check(lines.length() in 1..1000)
        val codes = HashSet<String>(); var total = 0L
        for (index in 0 until lines.length()) {
            val line = lines.getJSONObject(index); val code = line.getString("deliveryCode"); val count = line.getLong("plannedCount")
            check(codes.add(code) && code.matches(Regex("[0-9]{3}")) && count in 1..Int.MAX_VALUE)
            total = Math.addExact(total, count)
        }
        check(total == detail.getString("plannedTotal").toLong()) { "计划合计不一致" }
        check(bales.isNotEmpty())
    }
}
