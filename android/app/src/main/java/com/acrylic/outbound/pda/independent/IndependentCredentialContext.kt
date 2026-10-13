package com.acrylic.outbound.pda.independent

import com.acrylic.outbound.pda.network.SessionIdentity
import com.acrylic.outbound.pda.security.TimeSample
import com.acrylic.outbound.pda.security.TrustedTime
import org.json.JSONObject

internal data class IndependentCredentialContext(val session: JSONObject, val binding: Pair<String, String>, val sample: TimeSample) {
    fun requireProof(proof: IndependentCredentialProof) {
        val identity = proof.identity
        check(session.optString("blockedReason").isEmpty() && binding == (identity.deviceId to identity.deviceKeySha256))
        check(session.getString("userId") == identity.operatorId && session.getString("deviceId") == identity.deviceId &&
            session.getString("warehouseId") == identity.warehouseId && session.getString("serverRecoveryEpoch") == identity.serverRecoveryEpoch &&
            session.getString("userAuthVersion") == proof.userVersion && session.getString("deviceVersion") == proof.deviceVersion) {
            "独立采集身份、设备或恢复代次变化；原件保留"
        }
    }
    fun intent(): JSONObject {
        check(session.optString("blockedReason").isEmpty() && session.getString("localSessionId").isNotBlank())
        check(session.getString("deviceId") == binding.first)
        TrustedTime.now(session.getJSONObject("timeAnchor"), sample)
        val result = JSONObject()
        listOf("userId", "deviceId", "warehouseId", "serverRecoveryEpoch", "localSessionId", "userAuthVersion", "deviceVersion")
            .forEach { result.put(it, session.getString(it)) }
        return result.put("deviceKeySha256", binding.second)
    }
    fun requireIntent(intent: JSONObject) {
        SessionIdentity.requireSame(intent, session)
        check(intent.getString("userAuthVersion") == session.getString("userAuthVersion") &&
            intent.getString("deviceVersion") == session.getString("deviceVersion") &&
            binding == (intent.getString("deviceId") to intent.getString("deviceKeySha256"))) { "原预置请求身份已变化；原件保留" }
    }
}
