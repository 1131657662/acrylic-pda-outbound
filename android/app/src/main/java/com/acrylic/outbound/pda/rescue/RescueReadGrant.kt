package com.acrylic.outbound.pda.rescue

import org.json.JSONObject

data class RescueTarget(val source: String, val localOrderId: String)
data class RescueReadIdentity(val deviceId: String, val operatorId: String, val warehouseId: String)

/** Only the credential verifier creates this capability. It never grants scanning or submission. */
class RescueReadGrant internal constructor(val identity: RescueReadIdentity,
    orderIds: Set<String> = emptySet(), targets: Set<RescueTarget> = emptySet(), private val stillValid: () -> Unit) {
    private val orders = orderIds.toSet()
    private val scope = targets.toSet()
    init { require(listOf(identity.deviceId, identity.operatorId, identity.warehouseId).all { it.isNotBlank() }) }
    fun check(order: JSONObject) {
        stillValid()
        check(order.getString("deviceId") == identity.deviceId && order.getString("operatorId") == identity.operatorId &&
            order.getString("warehouseId") == identity.warehouseId &&
            (orders.isEmpty() || order.getString("localOrderId") in orders) &&
            (scope.isEmpty() || RescueTarget(order.getString("source"), order.getString("localOrderId")) in scope)) { "救援凭据不适用于本单" }
    }
    fun recheck() = stillValid()
}
