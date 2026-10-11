package com.acrylic.outbound.pda.rescue

import org.json.JSONObject
import java.math.BigInteger

object RescueFacts {
    fun summary(snapshot: RescueSnapshot): JSONObject {
        scope(snapshot.order)
        require(listOf(snapshot.captures, snapshot.scans, snapshot.events, snapshot.lines, snapshot.dispositions).all { it.size <= 100_000 })
        val captures = snapshot.captures
        requireUnique(captures, "id"); requireUnique(snapshot.scans, "captureId"); requireUnique(snapshot.events, "id")
        requireUnique(snapshot.lines, "deliveryCode")
        requireOrdered(captures, "orderScanSeq"); requireOrdered(snapshot.scans, "orderScanSeq")
        requireOrdered(snapshot.events, "orderPosition")
        validateScans(snapshot)
        val excluded = RescueDispositions.excluded(snapshot)
        val active = snapshot.scans.filter { it.getString("status") == "ACTIVE" && it.getString("captureId") !in excluded }
        return JSONObject().put("captures", captures.size.toString()).put("scans", snapshot.scans.size.toString())
            .put("events", snapshot.events.size.toString()).put("active", active.size.toString())
            .put("void", snapshot.scans.count { it.getString("status") == "VOID" }.toString()).put("disposed", excluded.size.toString())
            .put("pending", captures.count { it.getString("state") in setOf("RECEIVED", "WAITING_CONFIRM", "CONFIRM_REQUESTED") }.toString())
            .put("failed", captures.count { it.getString("state") in setOf("INVALID", "BUSINESS_REJECTED") }.toString())
            .put("weightGrams", active.fold(BigInteger.ZERO) { sum, row -> sum + integer(row, "weightGrams") }.toString())
    }

    private fun validateScans(snapshot: RescueSnapshot) {
        val byId = snapshot.captures.associateBy { it.getString("id") }
        val codes = snapshot.lines.map { it.getString("deliveryCode") }.toSet()
        snapshot.lines.forEach { integer(it, "plannedCount") }
        snapshot.scans.forEach { scan ->
            val capture = requireNotNull(byId[scan.getString("captureId")]) { "扫码缺少原采集输入" }
            require(scan.getString("orderScanSeq") == capture.getString("orderScanSeq"))
            require(scan.getString("status") in setOf("ACTIVE", "VOID"))
            require(scan.getString("deliveryCode") in codes) { "原扫码产品不在计划中" }
            require(integer(scan, "weightGrams") >= BigInteger.ZERO)
        }
    }

    private fun scope(order: JSONObject) {
        require(order.getString("source") in setOf("FORMAL", "TEMP", "INDEPENDENT"))
        require(order.getString("timeTrust") == "UNCONFIRMED_EXPORT_TIME")
        listOf("orderId", "localOrderId", "deviceId", "operatorId", "warehouseId", "state").forEach {
            require(order.getString(it).isNotBlank()) { "原订单范围缺失：$it" }
        }
    }

    fun scanTable(snapshot: RescueSnapshot): ByteArray {
        val disposed = snapshot.dispositions.associate { it.getString("captureId") to it.getString("action") }
        return RescueText.tsv(listOf("原扫码序号", "采集ID", "条码", "产品代码", "型号", "品名", "原重量", "整数克", "状态", "保存时间", "原处置"),
            snapshot.scans.map { row -> listOf("orderScanSeq", "captureId", "barcode", "deliveryCode", "model", "name", "rawWeight", "weightGrams", "status", "savedAt").map { row.getString(it) } + disposed[row.getString("captureId")].orEmpty() })
    }

    fun productTable(snapshot: RescueSnapshot): ByteArray {
        val byCode = snapshot.scans.groupBy { it.getString("deliveryCode") }
        val excluded = RescueDispositions.excluded(snapshot)
        return RescueText.tsv(listOf("产品代码", "计划包数", "有效包数", "撤销包数", "处置包数", "差额", "有效整数克"), snapshot.lines.map { line ->
            val rows = byCode[line.getString("deliveryCode")].orEmpty()
            val active = rows.filter { it.getString("status") == "ACTIVE" && it.getString("captureId") !in excluded }
            listOf(line.getString("deliveryCode"), line.getString("plannedCount"), active.size.toString(),
                rows.count { it.getString("status") == "VOID" }.toString(), rows.count { it.getString("captureId") in excluded }.toString(),
                (integer(line, "plannedCount") - active.size.toBigInteger()).toString(),
                active.fold(BigInteger.ZERO) { sum, row -> sum + integer(row, "weightGrams") }.toString())
        })
    }

    fun integer(row: JSONObject, key: String): BigInteger {
        val text = row.getString(key)
        require(text.matches(Regex("0|[1-9][0-9]{0,18}"))) { "无效整数字段：$key" }
        return text.toBigInteger().also { require(it <= Long.MAX_VALUE.toBigInteger()) }
    }

    private fun requireUnique(rows: List<JSONObject>, field: String) {
        val values = rows.map { it.getString(field) }
        require(values.all { it.isNotBlank() } && values.toSet().size == values.size) { "原事实ID重复或缺失" }
    }

    private fun requireOrdered(rows: List<JSONObject>, field: String) {
        val values = rows.map { integer(it, field) }
        require(values.all { it > BigInteger.ZERO } && values.zipWithNext().all { (a, b) -> a < b }) { "原顺序错误" }
    }
}
