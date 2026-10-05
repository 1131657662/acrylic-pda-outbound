package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** Cross-device approval is scoped to the signed imported case, never to ordinary continuation. */
internal object HandoverContinuationVerifier {
    fun verify(stage: LocalHandoverStage, original: JSONObject, command: JSONObject,
        receipt: JSONObject, session: JSONObject, now: Instant): JSONObject {
        val key = session.getJSONObject("signingKey")
        val proof = JwtVerifier.verify(receipt.getString("signedReceipt"), key, "acrylic-pda-handover-continuation")
        val plain = JSONObject(receipt.toString()).apply { remove("signedReceipt") }
        check(SubmissionJson.canonical(plain) == SubmissionJson.canonical(proof.getJSONObject("receipt"))) { "续作回执与签名不符" }
        check(receipt.getString("status") == "ACTIVATED" && receipt.getString("scope") == "pda:scan" &&
            receipt.get("stockReleased") == false) { "续作回执状态无效" }
        check(receipt.getString("caseId") == stage.caseId && receipt.getString("targetDeviceId") == stage.targetDeviceId &&
            receipt.getString("targetUserId") == stage.targetUserId) { "续作回执目标不匹配" }
        UUID.fromString(receipt.getString("continuationId"))
        for ((field, source) in listOf("previousAssignmentId" to "assignmentId", "assignmentId" to "assignmentId",
            "previousAuthorizationId" to "authorizationId", "recoveryPackageSha256" to "payloadSha256",
            "retainedManifestSha256" to "retainedManifestSha256", "nextOrderScanSeq" to "nextOrderScanSeq"))
            check(receipt.get(field).toString() == command.get(source).toString()) { "续作回执绑定错误：$field" }
        return assignment(original, receipt, session, now)
    }

    private fun assignment(original: JSONObject, receipt: JSONObject, session: JSONObject, now: Instant): JSONObject {
        val assignment = receipt.getJSONObject("assignment")
        val key = session.getJSONObject("signingKey")
        val claims = JwtVerifier.authorization(assignment, session, key, now)
        val previous = JwtVerifier.verify(original.getString("offlineToken"), key, "acrylic-pda-offline")
        for (field in listOf("id", "orderId", "operatorId", "deviceId", "epoch", "revision", "serverRecoveryEpoch",
            "inventorySnapshotId", "inventorySha256", "planSha256", "startOrderScanSeq", "allocationVersion", "expiresAt"))
            check(assignment.get(field).toString() == original.get(field).toString()) { "续作固定授权发生变化：$field" }
        check(assignment.getString("id") == receipt.getString("assignmentId") &&
            assignment.getString("offlineAuthorizationId") == receipt.getString("authorizationId") &&
            assignment.getString("offlineToken") == receipt.getString("signedToken") &&
            assignment.getString("expiresAt") == receipt.getString("expiresAt") &&
            assignment.getString("startOrderScanSeq").toLong() == receipt.getLong("nextOrderScanSeq"))
        check(claims.getString("jti") != previous.getString("jti") &&
            claims.getString("renewedFromAuthorizationId") == previous.getString("jti") &&
            claims.getLong("authorizationSequence") == Math.addExact(previous.optLong("authorizationSequence", 0), 1) &&
            claims.getLong("authorizationSequence") == assignment.getLong("authorizationSequence") &&
            claims.getLong("iat") >= previous.getLong("iat")) { "续作凭据未承接原导入授权" }
        return assignment
    }
}
