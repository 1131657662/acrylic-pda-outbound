package com.acrylic.outbound.pda.renewal

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.data.PreparedOrder
import com.acrylic.outbound.pda.network.PdaSession
import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONArray
import org.json.JSONObject

/** Renews an online-bound credential and swaps it only after the signed response is verified. */
class AuthorizationRenewalRepository(private val db: PdaDatabase, private val session: PdaSession) {
    fun renew(order: PreparedOrder): PreparedOrder {
        val current = JSONObject(order.assignmentJson)
        val rootId = order.assignmentId
        val pending = db.captures().pendingCount(rootId)
        check(pending == 0 && db.captures().failure(rootId) == null) { "仍有未处理输入，先完成或明确拒绝后再续期" }
        check(db.work().order(rootId)?.state?.let { it == "SCANNING" } != false) { "当前作业已停止，请先完成恢复流程" }
        val auth = session.requireSession()
        check(current.getString("operatorId") == auth.getString("userId") && current.getString("deviceId") == auth.getString("deviceId")) {
            "请由原操作员在原设备办理续期"
        }
        val key = "renewal:${current.getString("offlineAuthorizationId")}"
        val command = JSONObject().put("authorizationId", current.getString("offlineAuthorizationId"))
            .put("assignmentEpoch", current.getString("epoch")).put("orderId", current.getString("orderId"))
            .put("revision", current.getInt("revision")).put("pendingInputCount", pending)
        val renewed = session.call("pda/assignments/${current.getString("id")}/renewal", command,
            current.getString("serverRecoveryEpoch"), null, mapOf("Idempotency-Key" to key))
        verify(current, renewed)
        val history = JSONArray(order.authorizationHistoryJson)
        history.put(current)
        val currentJson = renewed.toString()
        db.runInTransaction {
            val continuation = db.continuations().current(rootId)
            if (continuation == null) db.preparations().replaceAuthorization(rootId, currentJson, history.toString())
            else db.continuations().replaceAuthorization(continuation.id, currentJson, history.toString())
        }
        return if (db.continuations().current(rootId) == null) db.preparations().order(rootId)!! else order.copy(assignmentJson = currentJson)
    }

    private fun verify(previous: JSONObject, renewed: JSONObject) {
        check(renewed.getString("id") == previous.getString("id")) { "续期响应绑定的作业不一致" }
        check(renewed.getString("orderId") == previous.getString("orderId") && renewed.getString("deviceId") == previous.getString("deviceId")
            && renewed.getString("operatorId") == previous.getString("operatorId")) { "续期响应身份不一致" }
        check(renewed.getString("planSha256") == previous.getString("planSha256")
            && renewed.getString("inventorySha256") == previous.getString("inventorySha256")) { "续期改变了固定资料" }
        JwtVerifier.authorization(renewed, session.requireSession(), session.requireSession().getJSONObject("signingKey"), session.now())
        val jwt = renewed.getString("offlineToken").split('.')
        val claims = JSONObject(String(android.util.Base64.decode(jwt[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP), Charsets.UTF_8))
        check(claims.getLong("exp") - claims.getLong("iat") == 48L * 60 * 60) { "续期有效期不是固定48小时" }
        check(claims.getString("renewedFromAuthorizationId") == previous.getString("offlineAuthorizationId")) { "续期未绑定原授权" }
    }
}
