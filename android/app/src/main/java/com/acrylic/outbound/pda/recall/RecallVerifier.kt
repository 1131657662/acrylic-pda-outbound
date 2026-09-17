package com.acrylic.outbound.pda.recall

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject

object RecallVerifier {
    fun verify(local: LocalRecall, response: JSONObject, key: JSONObject) {
        val claims = JwtVerifier.verify(response.getString("signedReceipt"), key, "acrylic-pda-recall")
        val plain = JSONObject(response.toString()).apply { remove("signedReceipt") }
        check(SubmissionJson.canonical(plain) == SubmissionJson.canonical(claims.getJSONObject("receipt"))) { "停止证明内容与签名不符" }
        val body = JSONObject(checkNotNull(local.payload))
        mapOf("changeRequestId" to local.id, "assignmentId" to body.getString("assignmentId"), "recoveryPackageId" to local.packageId,
            "payloadSha256" to local.sha256, "orderId" to body.getString("orderId"),
            "issuedRecoveryEpoch" to body.getString("sourceRecoveryEpoch"), "actualCount" to body.getString("expectedCount"),
            "actualWeightGrams" to body.getString("expectedWeightGrams"), "captureHighWatermark" to body.getString("captureHighWatermark"),
            "auditThroughSeq" to body.getJSONArray("auditCoverage").getJSONObject(0).getString("throughSeq"))
            .forEach { (field, expected) -> check(response.getString(field) == expected) { "停止证明绑定错误：$field" } }
        check(response.getString("status") == "STOPPED" && !response.getBoolean("mayResumeScanning") && !response.getBoolean("stockReleased")) { "停止证明状态错误" }
    }
}
