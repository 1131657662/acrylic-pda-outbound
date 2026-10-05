package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.data.PreparedOrder
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject

internal object HandoverContinuationCommand {
    fun body(stage: LocalHandoverStage, prepared: PreparedOrder, id: String): String {
        val assignment = JSONObject(prepared.assignmentJson)
        val recovery = JSONObject(stage.recoveryBytes.toString(Charsets.UTF_8))
        return SubmissionJson.canonical(JSONObject().put("commandVersion", 1).put("commandId", id)
            .put("assignmentId", prepared.assignmentId).put("authorizationId", assignment.getString("offlineAuthorizationId"))
            .put("payloadSha256", stage.recoverySha256).put("retainedManifestSha256", ContinuationVerifier.retainedHash(recovery))
            .put("nextOrderScanSeq", Math.addExact(stage.inputHighWatermark, 1)).put("planSha256", assignment.getString("planSha256")))
    }

    fun identity(session: JSONObject): String {
        check(!session.has("blockedReason")) { "登录状态已失效，请重新登录并核对" }
        val identity = JSONObject()
        for (field in listOf("userId", "deviceId", "warehouseId", "serverRecoveryEpoch", "userAuthVersion", "deviceVersion"))
            identity.put(field, session.getString(field))
        return SubmissionJson.canonical(identity)
    }

    fun verify(row: LocalHandoverContinuation, stage: LocalHandoverStage, prepared: PreparedOrder, session: JSONObject) {
        check(row.identityJson == identity(session)) { "原续作命令身份或权限已变化，请保留资料核对" }
        check(row.commandJson == body(stage, prepared, row.commandId) &&
            row.commandSha256 == JwtVerifier.sha(row.commandJson.toByteArray(Charsets.UTF_8))) {
            "原续作命令或本机资料已变化，请保留资料"
        }
    }
}
