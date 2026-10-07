package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject

object TemporarySubmissionReceipt {
    fun verify(local: TemporarySubmission, response: JSONObject, key: JSONObject, mappedOrderId: String? = null): String {
        val body = JSONObject(local.payload); val status = response.getString("status")
        check(response.getString("submissionId") == local.id && response.getString("freezeDeviceId") == body.getString("freezeDeviceId") &&
            response.getString("workRound") == body.getString("workRound") && response.getString("issuedRecoveryEpoch") == body.getString("sourceRecoveryEpoch") &&
            SubmissionJson.canonical(response.getJSONObject("source")) == SubmissionJson.canonical(body.getJSONObject("source"))) { "云端回执原始临时来源不一致" }
        check(!response.getBoolean("mayResumeScanning") && !response.getBoolean("stockReleased")) { "回执不能解除实物或扫码保护" }
        check(status in setOf("REGISTERED", "RECEIVING", "REVIEW_REQUIRED", "ABORTED")) { "临时单尚需独立确认，请保留原记录联系主管" }
        if (!response.isNull("payloadSha256")) check(response.getString("payloadSha256") == local.sha256) { "云端冻结正文摘要不一致" }
        if (status == "REVIEW_REQUIRED") {
            TemporaryVerifier.uuid(response.getString("orderId"))
            check(response.getString("payloadSha256") == local.sha256) { "云端尚未确认完整正文" }
        }
        if (status == "ABORTED") {
            val signed = JwtVerifier.verify(response.getString("signedReceipt"), key, "acrylic-pda-receipt", 1024 * 1024).getJSONObject("receipt")
            val unsigned = JSONObject(response.toString()).apply { remove("signedReceipt") }
            check(SubmissionJson.canonical(signed) == SubmissionJson.canonical(unsigned)) { "取消回执签名与响应不一致" }
            check(response.getBoolean("payloadPresent") == !response.isNull("payloadSha256")) { "取消回执正文状态不一致" }
        }
        local.receipt?.let { previous ->
            val old = JSONObject(previous)
            if (!old.isNull("orderId")) check(old.getString("orderId") == response.getString("orderId") ||
                mappedOrderId == response.getString("orderId")) { "临时单正式化目标发生变化" }
        }
        return status
    }
}
