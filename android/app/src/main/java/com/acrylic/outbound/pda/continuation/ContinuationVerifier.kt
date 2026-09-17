package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.recall.LocalRecall
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.*

object ContinuationVerifier {
    fun verify(local: LocalRecall, response: JSONObject, key: JSONObject): JSONObject {
        val claims = JwtVerifier.verify(response.getString("signedReceipt"), key, "acrylic-pda-continuation", 16 * 1024 * 1024)
        val plain = JSONObject(response.toString()).apply { remove("signedReceipt") }
        check(SubmissionJson.canonical(plain) == SubmissionJson.canonical(claims.getJSONObject("receipt"))) { "续作票据内容与签名不符" }
        val body = JSONObject(checkNotNull(local.payload))
        DispositionRecords.verifyExtension(body, DispositionRecords.rows(response))
        check(JwtVerifier.sha(local.payload.toByteArray(Charsets.UTF_8)) == local.sha256) { "原恢复正文损坏" }
        mapOf("previousAssignmentId" to body.getString("assignmentId"), "recoveryPackageId" to local.packageId,
            "payloadSha256" to local.sha256, "orderId" to body.getString("orderId"), "issuedRecoveryEpoch" to body.getString("sourceRecoveryEpoch"),
            "retainedManifestSha256" to retainedHash(body, DispositionRecords.rows(response))).forEach { (field, value) -> check(response.getString(field) == value) { "续作票据绑定错误：$field" } }
        check(response.getString("status") == "ACTIVATED" && !response.getBoolean("stockReleased")) { "续作票据未激活" }
        check(response.getString("nextOrderScanSeq").toLong() == Math.addExact(body.getString("captureHighWatermark").toLong(), 1)) { "续作起始序号不匹配" }
        val oldRound = body.optString("workRound", "1").toLong()
        check(response.getString("workRound").toLong() == Math.addExact(oldRound, 1)) { "续作轮次不匹配" }
        return response.getJSONObject("assignment").also {
            check(it.getString("orderId") == body.getString("orderId") && it.getString("deviceId") == body.getString("freezeDeviceId"))
            check(it.getString("id") != body.getString("assignmentId") && it.getString("epoch").toLong() > body.getString("assignmentEpoch").toLong())
            check(it.getInt("revision") == body.getInt("orderRevision") && it.getString("inventorySnapshotId") == body.getString("inventorySnapshotId"))
            check(it.getString("startOrderScanSeq") == response.getString("nextOrderScanSeq"))
        }
    }
    fun retainedHash(body: JSONObject, proofs: JSONArray = DispositionRecords.rows(body)): String {
        val retained = JSONArray(DispositionRecords.effective(body, proofs))
        return JwtVerifier.sha(SubmissionJson.canonical(retained).toByteArray(Charsets.UTF_8))
    }
}
