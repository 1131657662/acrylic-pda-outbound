package com.acrylic.outbound.pda.pairing

import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONObject
import java.util.UUID

object PairingVerification {
    fun sha(value: String) = JwtVerifier.sha(value.toByteArray(Charsets.UTF_8))
    fun binding(p: JSONObject): String = listOf("applicationId", "publicKey", "credential", "model", "approvalSha256")
        .joinToString("\n") { p.getString(it) }
    fun challenge(p: JSONObject, action: String, result: JSONObject, epoch: String): String {
        val text = result.getString("signedText"); val lines = text.split('\n')
        check(lines.size == 8 && lines[0] == "acrylic-pairing-proof-v1" && lines[1] == result.getString("challengeId") &&
            lines[2] == p.getString("applicationId") && lines[3] == action && lines[4].matches(Regex("[A-Za-z0-9_-]{43}")) &&
            lines[5] == sha(p.getString("publicKey")) && lines[6] == sha(binding(p)) && lines[7] == epoch) { "登记挑战与本机申请不匹配" }
        return text
    }
    fun result(saved: JSONObject, response: JSONObject) {
        val p = saved.getJSONObject("payload")
        check(response.getString("applicationId") == p.getString("applicationId") &&
            response.getString("keySha256") == sha(p.getString("publicKey")) &&
            response.getString("serverRecoveryEpoch") == saved.getString("epoch")) { "登记结果与本机申请不匹配" }
        val state = response.getString("status")
        check(state in setOf("WAITING", "PENDING", "COMPLETED", "EXPIRED", "CANCELLED")) { "未知登记状态" }
        check(response.getString("shortCode").matches(Regex("[0-9]{8}")))
        if (!response.isNull("approval")) approval(p, response)
        if (state in setOf("PENDING", "COMPLETED")) check(!response.isNull("approval"))
        if (state == "COMPLETED") {
            UUID.fromString(response.getString("deviceId"))
            check(p.getString("approvalSha256").isNotBlank() && p.getString("approvalSha256") == response.getString("approvalSha256")) { "请恢复原PDA确认信息" }
        } else check(response.isNull("deviceId"))
    }
    private fun approval(p: JSONObject, response: JSONObject) {
        val a = response.getJSONObject("approval")
        check(a.getString("applicationId") == p.getString("applicationId") && a.getString("keySha256") == response.getString("keySha256") &&
            a.getString("serverRecoveryEpoch") == response.getString("serverRecoveryEpoch"))
        UUID.fromString(a.getString("warehouseId"))
        val text = listOf(a.getString("applicationId"), a.getString("warehouseId"), a.getString("keySha256"),
            a.getString("serverRecoveryEpoch"), sha(a.getString("warehouseName")), sha(a.getString("deviceCode")), sha(a.getString("name"))).joinToString("\n")
        check(sha(text) == response.getString("approvalSha256")) { "待绑定仓库或设备信息被改变" }
        check(p.getString("approvalSha256").isEmpty() || p.getString("approvalSha256") == response.getString("approvalSha256")) { "原确认信息已变化" }
    }
}
