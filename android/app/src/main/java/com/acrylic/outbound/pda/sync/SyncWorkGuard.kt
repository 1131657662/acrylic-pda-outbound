package com.acrylic.outbound.pda.sync

import com.acrylic.outbound.pda.data.*
import org.json.JSONObject

class SyncWorkGuard(private val db: PdaDatabase) {
    fun requireAllowed(order: PreparedOrder) {
        RecoveryProtection(db).requireNormal()
        val assignment = JSONObject(order.assignmentJson)
        val epoch = assignment.getString("serverRecoveryEpoch")
        val status = db.sync().activeOrder(epoch, order.orderId)?.status ?: return
        val reason = when (status) {
            "RECALL_PENDING" -> "云端已要求停止，请联网核对召回"
            "CANCELLED" -> "云端订单已作废，请联系主管核对"
            "COMPLETED" -> "云端订单已完成，请核对原上传回执"
            "REVIEW_REQUIRED" -> "云端正在复核，请等待处理结果"
            "READY" -> "云端订单归属待核对，请联网刷新"
            "LINKED" -> "云端订单已关联，请联系主管核对"
            "CONTINUING" -> if (db.continuations().current(order.assignmentId) == null) "云端已有续作安排，请先核对并激活续作凭据" else null
            else -> null
        }
        check(reason == null) { reason!! }
    }
}
