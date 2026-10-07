package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.evidence.E2aReceiptVerifier
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject

/** Reconstructs the same technical stop from durable E2A roots and the immutable local reason. */
internal class TemporaryContinuationStopRequest(private val db: PdaDatabase) {
    fun body(local: TemporarySubmission, reason: String, trustedKey: JSONObject): JSONObject {
        val stop = checkNotNull(db.localE2aStops().get(local.clientOrderId)) { "缺少原冻结停止证明" }
        val plan = checkNotNull(db.localE2aTransfers().get(local.clientOrderId))
        val request = checkNotNull(db.localE2aTransfers().request(local.clientOrderId, "VERIFY", 0))
        val saved = checkNotNull(db.localE2aReceipts().get(request.originalKey)) { "请先完成原机证据补传核验" }
        check(saved.state == "VERIFIED" && stop.submissionId == local.id && stop.workRound > 1)
        check(request.originalKey == plan.verifyOriginalKey)
        val receipt = E2aReceiptVerifier.verify(E2aReceiptVerifier.Expected(stop, plan, request),
            checkNotNull(saved.responseBytes), trustedKey)
        check(receipt.getString("status") == "VERIFIED_COMPLETE_AT_STOP") { "原机证据尚未完整核验" }
        return JSONObject().put("submissionId", local.id).put("reason", reason)
            .put("sourceEvidenceSetId", receipt.getString("evidenceSetId"))
            .put("verificationId", receipt.getString("evidenceVerificationId")).put("setSha256", plan.setHash)
    }

    fun verify(local: TemporarySubmission, receipt: JSONObject, trustedKey: JSONObject) {
        check(receipt.get("localDeletionAllowed") == false && receipt.get("payloadPresent") == true)
        val original = body(local, checkNotNull(local.abortReason), trustedKey)
        check(receipt.getString("stopRequestSha256") == JwtVerifier.sha(SubmissionJson.canonical(original).toByteArray(Charsets.UTF_8))) {
            "云端停止回执与本机原请求不符"
        }
    }
}
