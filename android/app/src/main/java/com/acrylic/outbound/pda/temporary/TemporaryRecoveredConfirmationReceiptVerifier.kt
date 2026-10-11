package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.continuation.HandoverReceiptVerifier
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import java.time.Instant

/** A dedicated signed receipt is evidence only and cannot authorize ordinary work or activation. */
internal object TemporaryRecoveredConfirmationReceiptVerifier {
    data class Context(val row: TemporaryRecoveredConfirmation, val stage: TemporaryRecoveredHandoverStage, val manifest: JSONObject)
    data class Response(val bytes: ByteArray, val expected: JSONObject, val now: Instant,
        val key: JSONObject = expected.getJSONObject("signingKey"))
    fun verify(context: Context, input: Response): TemporaryRecoveredConfirmationReceipt {
        val row = context.row; val stage = context.stage; val manifest = context.manifest
        TemporaryRecoveredConfirmationCommand.require(row, stage, manifest)
        check(input.bytes.size in 1..8 * 1024 * 1024)
        val original = JSONObject(input.bytes.toString(Charsets.UTF_8))
        check(SubmissionJson.canonical(original).toByteArray(Charsets.UTF_8).contentEquals(input.bytes)) { "请保留原恢复确认回执字节" }
        TemporaryRecoveredHandoverQualification.fields(original, "formatVersion purpose receiptId recordedAt requestId status commandSha256 recipientSessionSha256 originalCommand qualification preparedTicket checklist capabilities actor signedReceipt")
        val receipt = HandoverReceiptVerifier.verify(original, input.key, "acrylic-pda-recovered-temporary-handover-confirmation", "acrylic-outbound")
        check(receipt.get("formatVersion") == 1 && receipt.getString("purpose") == "RECOVERED_TEMP_HANDOVER_CONFIRMATION"
            && receipt.getString("status") == "RECOVERED_TARGET_CONFIRMED")
        TemporaryVerifier.uuid(receipt.getString("receiptId"))
        val at = Instant.parse(receipt.getString("recordedAt")); check(!at.isAfter(input.now))
        check(!at.isBefore(Instant.parse(manifest.getJSONObject("preparationReceipt").getString("recordedAt"))))
        check(receipt.getString("requestId").matches(Regex("[A-Za-z0-9._:-]{1,64}")))
        check(receipt.getString("commandSha256") == row.commandSha256 && receipt.getString("recipientSessionSha256") == stage.recipientSessionSha256)
        check(SubmissionJson.canonical(receipt.getJSONObject("originalCommand")) == row.commandJson)
        for (field in listOf("preparedTicket", "checklist"))
            check(SubmissionJson.canonical(receipt.get(field)) == SubmissionJson.canonical(manifest.get(field)))
        check(SubmissionJson.canonical(receipt.getJSONObject("qualification")) ==
            SubmissionJson.canonical(manifest.getJSONObject("preparedTicket").getJSONObject("temporaryQualification")))
        actor(receipt.getJSONObject("actor"), input.expected)
        return TemporaryRecoveredConfirmationReceipt(row.caseId, JwtVerifier.sha(input.bytes), SubmissionJson.canonical(input.key), input.bytes.clone())
    }
    private fun actor(actor: JSONObject, expected: JSONObject) {
        val fields = "userId deviceId warehouseId userAuthVersion deviceVersion sessionId"
        TemporaryRecoveredHandoverQualification.fields(actor, fields)
        for (field in fields.split(' ')) check(actor.getString(field) == expected.getString(field)) { "原恢复确认身份、会话或版本变化，请保留资料" }
    }
}
