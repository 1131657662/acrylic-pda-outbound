package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.continuation.HandoverReceiptVerifier
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import java.time.Instant

/** Binds the receiving activation to the original staged prefix and the recipient's own confirmation. */
internal object TemporaryHandoverActivationProtocol {
    data class Input(val stage: TemporaryHandoverStage, val confirmation: TemporaryHandoverConfirmationReceipt,
        val expected: JSONObject)
    data class Verified(val receipt: JSONObject, val decision: JSONObject, val authorization: JSONObject)

    fun verify(bytes: ByteArray, input: Input): Verified {
        check(bytes.isNotEmpty() && bytes.size <= 8 * 1024 * 1024)
        val original = JSONObject(bytes.toString(Charsets.UTF_8))
        val signed = HandoverReceiptVerifier.verify(original, input.expected.getJSONObject("signingKey"),
            "acrylic-outbound-handover-receipt", "acrylic-outbound-server")
        val decision = signed.getJSONObject("businessDecision")
        check(signed.get("receiptVersion") == 1 && signed.getString("commandType") == "ACTIVATE" && signed.getString("status") == "RECORDED")
        check(decision.getString("kind") == "TEMP_HANDOVER_ACTIVATE" && decision.getString("status") == "ACTIVATED")
        context(signed, input.stage); confirmation(decision, input)
        check(decision.getString("sourcePrefixSha256") == prefix(input.stage))
        check(decision.getString("fromWorkRound") == input.stage.fromWorkRound.toString() &&
            decision.getString("toWorkRound") == input.stage.toWorkRound.toString() &&
            decision.getString("startOrderScanSeq") == Math.addExact(input.stage.inputHighWatermark, 1).toString())
        for (field in listOf("ticketId", "proofId", "newAuthorizationId", "newClientOrderId")) TemporaryVerifier.uuid(decision.getString(field))
        val authorization = decision.getJSONObject("authorization")
        val issued = TemporaryHandoverAuthorization.verify(original, authorization, input.expected.getJSONObject("signingKey"))
        val manifest = TemporaryVerifier.manifest(authorization, input.expected.getJSONObject("signingKey"), issued)
        TemporaryVerifier.identity(manifest, input.expected); term(manifest, input.stage)
        return Verified(original, decision, authorization)
    }

    private fun context(receipt: JSONObject, stage: TemporaryHandoverStage) {
        val context = receipt.getJSONObject("caseContext")
        val expected = mapOf("caseId" to stage.caseId, "scopeId" to stage.scopeId, "submissionId" to stage.submissionId,
            "canonicalOrderId" to stage.canonicalOrderId, "warehouseId" to stage.warehouseId, "sourceKind" to "TEMP",
            "fromWorkRound" to stage.fromWorkRound.toString(), "toWorkRound" to stage.toWorkRound.toString(),
            "preparedTicketSha256" to JSONObject(stage.manifestJson).getString("preparedTicketSha256"),
            "inputHighWatermark" to stage.inputHighWatermark.toString())
        expected.forEach { (field, value) -> check(context.getString(field) == value) { "交接激活与原来源不一致" } }
        check(context.getString("originalResultState") == "ABORTED" && context.getString("stopState") == "DEVICE_ACKED")
        check(context.getString("integrityState") == "COMPLETE" && context.getString("physicalState") == "MATCHED")
        val before = JSONObject(stage.manifestJson).getJSONObject("preparationReceipt").getJSONObject("caseContext").getString("caseVersion").toLong()
        check(context.getString("caseVersion") == Math.addExact(before, 1).toString())
    }

    private fun confirmation(decision: JSONObject, input: Input) {
        check(JwtVerifier.sha(input.confirmation.receiptBytes) == input.confirmation.receiptSha256)
        val saved = HandoverReceiptVerifier.verify(JSONObject(input.confirmation.receiptBytes.toString(Charsets.UTF_8)),
            JSONObject(input.confirmation.signingKeyJson), "acrylic-pda-temporary-handover-confirmation", "acrylic-outbound")
        check(saved.getString("status") == "TARGET_CONFIRMED" && saved.getString("caseId") == input.stage.caseId)
        val actor = saved.getJSONObject("actor")
        for ((target, field) in listOf("targetUserId" to "userId", "targetDeviceId" to "deviceId", "targetSessionId" to "sessionId"))
            check(decision.getString(target) == actor.getString(field) && actor.getString(field) == input.expected.getString(field)) {
                "激活未绑定本人原确认和在线会话，保留原件"
            }
    }

    private fun prefix(stage: TemporaryHandoverStage): String {
        val prefix = JSONObject().put("scopeId", stage.scopeId).put("submissionId", stage.submissionId)
            .put("canonicalOrderId", stage.canonicalOrderId).put("fromWorkRound", stage.fromWorkRound.toString())
            .put("inputHighWatermark", stage.inputHighWatermark.toString()).put("payloadSha256", stage.payloadSha256)
            .put("checklistSha256", JwtVerifier.sha(SubmissionJson.canonical(JSONObject(stage.manifestJson).getJSONArray("checklist")).toByteArray(Charsets.UTF_8)))
        return JwtVerifier.sha(SubmissionJson.canonical(prefix).toByteArray(Charsets.UTF_8))
    }

    private fun term(manifest: JSONObject, stage: TemporaryHandoverStage) {
        val own = JSONObject(stage.manifestJson).getJSONObject("preparedTicket").getJSONObject("temporaryQualification")
        check(!Instant.parse(manifest.getString("expiresAt")).isAfter(Instant.parse(own.getString("expiresAt"))))
        val body = JSONObject(stage.payloadBytes.toString(Charsets.UTF_8))
        for (field in listOf("inventorySnapshotId", "policyVersion")) check(manifest.getString(field) == body.getString(field))
    }
}
