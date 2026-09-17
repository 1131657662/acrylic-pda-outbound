package com.acrylic.outbound.pda.work

import com.acrylic.outbound.pda.data.*
import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONObject
import java.util.UUID

class WorkStore(val db: PdaDatabase, val access: WorkAccess) {
    fun prepared(id: String): PreparedOrder = checkNotNull(db.preparations().order(id)) { "订单资料尚未激活" }

    fun open(id: String) {
        db.runInTransaction {
            val prepared = prepared(id); access.identity(prepared)
            check(db.captures().unresolvedOther(id) == null) { "另一订单仍有未处理输入，请先恢复原订单" }
            if (db.work().order(id) != null) { verifyTotals(id); return@runInTransaction }
            access.authorize(prepared)
            check(db.preparations().count(id) == prepared.rowCount) { "本机资料不完整" }
            val assignment = JSONObject(prepared.assignmentJson)
            check(JwtVerifier.sha(prepared.planJson.toByteArray(Charsets.UTF_8)) == assignment.getString("planSha256")) { "本地计划摘要不一致" }
            val plan = JSONObject(prepared.planJson); val order = plan.getJSONObject("order")
            check(plan.getJSONObject("policy").getString("scanMode") == "AUTO_SCAN_MANUAL_CONFIRM") { "不支持的扫码政策" }
            val detail = order.getJSONObject("plan")
            db.work().insertOrder(WorkOrder(id, prepared.orderId, detail.getString("vehicleNo"), detail.getString("plannedTotal").toLong(),
                assignment.getString("startOrderScanSeq").toLong()))
            val lines = order.getJSONArray("lines")
            db.work().insertLines((0 until lines.length()).map { index -> lines.getJSONObject(index).let {
                WorkLine(id, it.getString("deliveryCode"), it.getString("model"), it.getString("name"), it.getLong("plannedCount")) } })
            db.work().insertDevice(WorkDevice(prepared.deviceId))
            event(id, null, "WORK_OPENED")
        }
    }

    fun verifyTotals(id: String) {
        val work = checkNotNull(db.work().order(id)); val total = db.scans().total(id)
        check(work.actualCount == total.count && work.weightGrams == total.grams) { "本机明细与汇总不一致，停止作业并保留数据" }
        val lines = db.work().lines(id)
        check(lines.sumOf { it.actualCount } == total.count && lines.fold(0L) { n, line -> Math.addExact(n, line.weightGrams) } == total.grams)
        val products = db.scans().products(id).associateBy { it.deliveryCode }
        check(products.keys.all { code -> lines.any { it.deliveryCode == code } })
        check(lines.all { it.actualCount == (products[it.deliveryCode]?.count ?: 0L) && it.weightGrams == (products[it.deliveryCode]?.grams ?: 0L) }) { "本机产品汇总不一致，停止作业并核对" }
    }

    /** Must be called inside the same transaction as the business fact. */
    fun event(assignment: String, capture: String?, type: String, detail: String = ""): Long {
        val order = prepared(assignment); val device = checkNotNull(db.work().device(order.deviceId)); val time = access.sample()
        val sequence = device.nextEventSeq
        db.work().insertEvent(WorkEvent(UUID.randomUUID().toString(), assignment, capture, order.deviceId, order.operatorId,
            sequence, type, detail, time.wall, time.elapsed, time.boot))
        db.work().updateDevice(device.copy(nextEventSeq = Math.addExact(sequence, 1)))
        return sequence
    }
}
