package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject

/** Shares only the complete E2A proof reader. The receiving stop binds its independent handover reference. */
internal class TemporaryHandoverStopRequest(private val db: PdaDatabase) {
    fun body(local: TemporarySubmission, reason: String, key: JSONObject): JSONObject {
        val original = JSONObject(local.payload)
        check(original.has("temporaryHandover") && !original.has("temporaryContinuation"))
        return TemporaryContinuationStopRequest(db).body(local, reason, key)
            .put("temporaryHandover", original.getJSONObject("temporaryHandover"))
    }
    fun verify(local: TemporarySubmission, receipt: JSONObject, key: JSONObject) {
        check(receipt.get("localDeletionAllowed") == false)
        if (receipt.get("payloadPresent") == false) {
            check(receipt.isNull("payloadSha256") && receipt.getString("localFrozenPayloadSha256") == local.sha256) {
                "交接停止回执未绑定本机独立冻结原件"
            }
        } else check(receipt.get("payloadPresent") == true && receipt.getString("payloadSha256") == local.sha256)
        val request = body(local, checkNotNull(local.abortReason), key)
        check(receipt.getString("stopRequestSha256") == JwtVerifier.sha(SubmissionJson.canonical(request).toByteArray(Charsets.UTF_8))) {
            "交接停止回执与独立原请求不一致"
        }
    }
}
