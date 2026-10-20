package com.acrylic.outbound.pda.work

import com.acrylic.outbound.pda.data.PreparedOrder
import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONObject

/** Reads the original downloaded plan by assignment and order identity; never infers a number. */
internal object FormalOrderNumber {
    fun read(prepared: PreparedOrder, order: WorkOrder): String? {
        check(prepared.assignmentId == order.assignmentId && prepared.orderId == order.orderId) { "原订单关联不一致" }
        val assignment = JSONObject(prepared.assignmentJson)
        check(assignment.getString("id") == order.assignmentId && assignment.getString("orderId") == order.orderId) { "原分配关联不一致" }
        check(JwtVerifier.sha(prepared.planJson.toByteArray(Charsets.UTF_8)) == assignment.getString("planSha256")) { "原计划摘要不一致" }
        val header = JSONObject(prepared.planJson).getJSONObject("order").getJSONObject("order")
        check(header.getString("id") == order.orderId) { "原计划订单关联不一致" }
        return header.opt("orderNo").let { it as? String }?.takeIf { it.isNotBlank() }
    }
}
