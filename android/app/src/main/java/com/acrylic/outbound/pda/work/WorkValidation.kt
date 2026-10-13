package com.acrylic.outbound.pda.work

import com.acrylic.outbound.pda.data.PreparedBale
import org.json.JSONObject

data class ValidatedCapture(val capture: WorkCapture, val order: WorkOrder, val line: WorkLine?, val bale: PreparedBale?, val error: String?)

class WorkValidation(private val store: WorkStore) {
    fun validate(capture: WorkCapture): ValidatedCapture {
        val c = capture.context; val prepared = store.prepared(c.assignmentId)
        store.access.authorize(prepared) // Unknown/revoked identity or time remains pending; never consume it as a barcode error.
        check(store.db.temporaryScans().unresolved() == 0) { "临时单仍有未处理输入，请先恢复原单" }
        check(store.db.independentScans().unresolved() == 0) { "独立来源仍有未处理输入，请先恢复原任务" }
        val order = checkNotNull(store.db.work().order(c.assignmentId))
        val assignment = JSONObject(prepared.assignmentJson)
        check(order.state == "SCANNING" && c.orderId == prepared.orderId && c.operatorId == prepared.operatorId &&
            c.originDeviceId == prepared.deviceId && c.assignmentEpoch == assignment.getString("epoch") &&
            c.snapshotId == assignment.getString("inventorySnapshotId") && c.sourceRecoveryEpoch == assignment.getString("serverRecoveryEpoch") &&
            c.authorizationId == assignment.getString("offlineAuthorizationId") && c.orderRevision == assignment.getInt("revision")) { "原扫码上下文已变化，保留记录并对账" }
        val bale = store.db.preparations().bale(c.assignmentId, c.barcode)
        val line = bale?.let { store.db.work().line(c.assignmentId, it.deliveryCode) }
        val cloudReason = com.acrylic.outbound.pda.sync.BarcodeWorkGuard(store.db).reason(c)
        val error = when {
            !c.barcode.matches(Regex("[0-9A-Z]{11}")) || !c.rawInput.trim(' ', '\r', '\n', '\t').matches(Regex("[0-9A-Za-z]{11}")) -> "条码必须是完整11位数字或英文字母"
            bale == null -> "条码不在本单固定库存中"
            line == null -> "产品不属于本车计划"
            bale.dispatchCycle != c.expectedDispatchCycle -> "出库周期不一致，请保留记录并核对"
            !bale.available -> "该包在准备时已不可用"
            cloudReason != null -> cloudReason
            store.db.scans().guard(c.barcode) != null || store.db.temporaryScans().guard(c.barcode) != null ||
                store.db.temporaryHandoverGuards().get(c.barcode) != null ||
                store.db.independentScans().guard(c.barcode) != null -> "该条码已在本机订单中保存，不能重复装车"
            line.actualCount >= line.plannedCount -> "该产品已达计划，禁止超装"
            order.actualCount >= order.plannedTotal -> "整单已达计划，禁止超装"
            else -> null
        }
        return ValidatedCapture(capture, order, line, bale, error)
    }
}
