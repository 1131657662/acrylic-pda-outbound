package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.network.SessionIdentity
import com.acrylic.outbound.pda.sync.RecoveryProtection
import org.json.JSONObject

class TemporaryAccess(private val db: PdaDatabase, private val connection: TemporaryConnection) {
    fun snapshot(): JSONObject = JSONObject(checkNotNull(connection.current()) { "请先登录" }.toString()).also { check(it) }
    fun check(expected: JSONObject) {
        val current = connection.current(); SessionIdentity.requireSame(expected, current)
        checkNotNull(current); check(!current.has("blockedReason")) { current.optString("blockedReason") }
        check(com.acrylic.outbound.pda.network.PdaCapabilities.temporary(current)) { "当前账号或设备未开启临时作业，请联系主管核对后重新登录" }
        for (field in listOf("userAuthVersion", "deviceVersion")) check(current.getString(field) == expected.getString(field)) { "人员或设备版本变化，请保留资料" }
        RecoveryProtection(db).requireNormal()
    }
    fun request(row: TemporaryRequest, expected: JSONObject) {
        check(expected)
        val fields = mapOf("userId" to row.userId, "deviceId" to row.deviceId, "warehouseId" to row.warehouseId,
            "serverRecoveryEpoch" to row.recoveryEpoch, "userAuthVersion" to row.userAuthVersion, "deviceVersion" to row.deviceVersion)
        fields.forEach { (field, value) -> check(expected.getString(field) == value) { "原临时准备属于旧身份或版本，保留资料并联系主管" } }
    }
}
