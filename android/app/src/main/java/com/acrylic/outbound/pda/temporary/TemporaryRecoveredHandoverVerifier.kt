package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import java.time.Instant

internal object TemporaryRecoveredHandoverVerifier {
    data class Originals(val manifest: ByteArray, val payload: ByteArray)
    fun verify(original: Originals, expected: JSONObject, now: Instant): TemporaryRecoveredHandoverStage {
        check(original.manifest.isNotEmpty() && original.manifest.size <= 2 * 1024 * 1024)
        val manifest = JSONObject(original.manifest.toString(Charsets.UTF_8))
        check(SubmissionJson.canonical(manifest).toByteArray(Charsets.UTF_8).contentEquals(original.manifest)) { "恢复交接清单须保留原固定字节" }
        TemporaryRecoveredHandoverQualification.fields(manifest, "caseId status sourceKind packageKind recoveryPackageSha256 inputHighWatermark preparedTicketSha256 stopKind recipientSessionSha256 preparedTicket checklist faultReceipt approvalReceipt preparationReceipt capabilities")
        TemporaryRecoveredHandoverQualification.hash(manifest, "recipientSessionSha256")
        val receipts = TemporaryHandoverReceipts.verify(manifest, expected)
        TemporaryRecoveredHandoverQualification.verify(receipts.qualification, expected, now)
        check(!now.isBefore(Instant.parse(receipts.preparation.getString("recordedAt"))))
        val approval = manifest.getJSONObject("approvalReceipt").getJSONObject("actor")
        check(approval.getString("principalId") == receipts.preparation.getJSONObject("caseContext").getString("approvedBy"))
        val body = TemporaryRecoveredHandoverPayload.verify(manifest, original.payload, receipts)
        return row(manifest, expected, original).also { check(it.sourceRecoveryEpoch == body.getString("sourceRecoveryEpoch")) }
    }
    private fun row(manifest: JSONObject, expected: JSONObject, original: Originals): TemporaryRecoveredHandoverStage {
        val qualification = manifest.getJSONObject("preparedTicket").getJSONObject("temporaryQualification")
        return TemporaryRecoveredHandoverStage(manifest.getString("caseId"), qualification.getString("qualificationId"),
            qualification.getString("qualificationSha256"), manifest.getString("recipientSessionSha256"),
            qualification.getJSONObject("source").getString("sourceRecoveryEpoch"), qualification.getJSONObject("control").getString("transportRecoveryEpoch"),
            expected.getString("userId"), expected.getString("deviceId"), expected.getString("warehouseId"),
            expected.getString("userAuthVersion"), expected.getString("deviceVersion"), expected.getString("sessionId"), expected.getString("localSessionId"),
            JwtVerifier.sha(original.manifest), original.manifest.size, JwtVerifier.sha(original.payload), original.payload.size,
            SubmissionJson.canonical(expected.getJSONObject("signingKey")))
    }
}
