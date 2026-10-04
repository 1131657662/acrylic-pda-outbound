package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.preparation.VerifiedPackage
import com.acrylic.outbound.pda.work.WorkGuard
import com.acrylic.outbound.pda.work.WorkLine
import com.acrylic.outbound.pda.work.WorkOrder
import org.json.JSONObject

/** Derives frozen target totals from retained source facts and the verified target plan. */
internal class HandoverImportedWork(input: VerifiedPackage, body: JSONObject, rows: HandoverRecoveryRows) {
    val targetDevice = input.assignment.getString("deviceId")
    private val target = input.assignment.getString("id")
    private val plan = JSONObject(input.payloads.getValue("plan").toString(Charsets.UTF_8)).getJSONObject("order")
    private val excluded = rows.dispositions.map { it.captureId }.toSet()
    private val effective = rows.scans.filter { it.status == "ACTIVE" && it.captureId !in excluded }
    val order = WorkOrder(target, input.assignment.getString("orderId"), plan.getJSONObject("plan").getString("vehicleNo"),
        plan.getJSONObject("plan").getString("plannedTotal").toLong(), input.assignment.getString("startOrderScanSeq").toLong(),
        effective.size.toLong(), effective.fold(0L) { sum, scan -> Math.addExact(sum, scan.weightGrams) },
        if (body.isNull("startedAt")) null else body.getString("startedAt"), "HANDOVER_FROZEN")
    val lines = (0 until plan.getJSONArray("lines").length()).map { index ->
        val line = plan.getJSONArray("lines").getJSONObject(index)
        val scans = effective.filter { it.deliveryCode == line.getString("deliveryCode") }
        WorkLine(target, line.getString("deliveryCode"), line.getString("model"), line.getString("name"),
            line.getLong("plannedCount"), scans.size.toLong(), scans.fold(0L) { sum, scan -> Math.addExact(sum, scan.weightGrams) })
    }.sortedBy { it.deliveryCode }
    // Historical dispositions keep guards, matching the original device's continuation rules.
    val guards = rows.scans.filter { it.status == "ACTIVE" }.map {
        WorkGuard(it.barcode, it.captureId, target, it.expectedDispatchCycle)
    }

    init {
        check(order.actualCount == body.getString("expectedCount").toLong() &&
            order.weightGrams == body.getString("expectedWeightGrams").toLong()) { "原事实与导入统计不一致" }
        check(lines.map { it.deliveryCode }.toSet().containsAll(effective.map { it.deliveryCode })) { "目标计划缺少原包产品" }
        check(lines.all { it.actualCount <= it.plannedCount } && order.actualCount <= order.plannedTotal) {
            "原包超出目标计划，请核对实物与计划"
        }
    }
}
