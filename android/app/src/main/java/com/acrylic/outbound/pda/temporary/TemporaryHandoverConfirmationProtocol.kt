package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.continuation.HandoverReceiptVerifier
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** Evidence binding only: this receipt can never be consumed as an activation credential. */
internal object TemporaryHandoverConfirmationProtocol {
    fun create(stage: TemporaryHandoverStage, expected: JSONObject): TemporaryHandoverConfirmation {
        val manifest = JSONObject(stage.manifestJson)
        val version = TemporaryVerifier.positive(manifest.getJSONObject("preparationReceipt").getJSONObject("caseContext").getString("caseVersion"))
        val command = JSONObject().put("commandVersion", 1).put("commandId", UUID.randomUUID().toString())
            .put("expectedCaseVersion", Math.addExact(version, 1).toString()).put("targetAuthorizationId", stage.targetAuthorizationId)
            .put("preparedTicketSha256", manifest.getString("preparedTicketSha256"))
            .put("payloadSha256", stage.payloadSha256).put("checklistSha256", sha(manifest.getJSONArray("checklist")))
            .put("physicalConfirmed", true)
        val bound = JSONObject().put("caseId", stage.caseId).put("transportRecoveryEpoch", expected.getString("serverRecoveryEpoch"))
            .put("command", command)
        return TemporaryHandoverConfirmation(stage.caseId, command.getString("commandId"), SubmissionJson.canonical(bound), sha(bound))
    }

    fun request(row: TemporaryHandoverConfirmation, stage: TemporaryHandoverStage, expected: JSONObject): JSONObject {
        val bound = JSONObject(row.boundCommandJson); val command = bound.getJSONObject("command")
        check(sha(bound) == row.commandSha256 && bound.getString("caseId") == row.caseId && row.caseId == stage.caseId)
        check(bound.getString("transportRecoveryEpoch") == expected.getString("serverRecoveryEpoch")) { "确认请求来自旧恢复代次，原件保留" }
        check(command.getString("commandId") == row.commandId && command.get("physicalConfirmed") == true && command.get("commandVersion") == 1)
        check(command.getString("targetAuthorizationId") == stage.targetAuthorizationId && command.getString("payloadSha256") == stage.payloadSha256)
        val manifest = JSONObject(stage.manifestJson)
        check(command.getString("preparedTicketSha256") == manifest.getString("preparedTicketSha256") &&
            command.getString("checklistSha256") == sha(manifest.getJSONArray("checklist"))) { "确认原意图与暂存资料不一致" }
        val version = manifest.getJSONObject("preparationReceipt").getJSONObject("caseContext").getString("caseVersion").toLong()
        check(command.getString("expectedCaseVersion") == Math.addExact(version, 1).toString())
        return command
    }

    fun receipt(row: TemporaryHandoverConfirmation, stage: TemporaryHandoverStage, input: Response): TemporaryHandoverConfirmationReceipt {
        request(row, stage, input.expected)
        check(input.bytes.isNotEmpty() && input.bytes.size <= 8 * 1024 * 1024)
        val original = JSONObject(input.bytes.toString(Charsets.UTF_8))
        val signed = HandoverReceiptVerifier.verify(original, input.key, "acrylic-pda-temporary-handover-confirmation", "acrylic-outbound")
        check(signed.get("receiptVersion") == 1 && signed.getString("status") == "TARGET_CONFIRMED")
        TemporaryVerifier.uuid(signed.getString("receiptId")); Instant.parse(signed.getString("recordedAt"))
        check(signed.getString("caseId") == row.caseId && signed.getString("commandSha256") == row.commandSha256)
        check(SubmissionJson.canonical(signed.getJSONObject("originalCommand")) == row.boundCommandJson)
        val manifest = JSONObject(stage.manifestJson)
        check(SubmissionJson.canonical(signed.getJSONObject("preparedTicket")) == SubmissionJson.canonical(manifest.getJSONObject("preparedTicket")))
        check(SubmissionJson.canonical(signed.getJSONArray("checklist")) == SubmissionJson.canonical(manifest.getJSONArray("checklist")))
        for (field in listOf("userId", "deviceId", "warehouseId", "userAuthVersion", "deviceVersion"))
            check(signed.getJSONObject("actor").getString(field) == input.expected.getString(field)) { "原确认身份或版本变化，请保留原件" }
        val flags = signed.getJSONObject("capabilities")
        check(flags.length() == 5 && flags.keys().asSequence().all { flags.get(it) == false }) { "确认回执不能授予作业或删除资格" }
        return TemporaryHandoverConfirmationReceipt(row.caseId, JwtVerifier.sha(input.bytes), SubmissionJson.canonical(input.key), input.bytes.clone())
    }

    private fun sha(value: Any) = JwtVerifier.sha(SubmissionJson.canonical(value).toByteArray(Charsets.UTF_8))
    data class Response(val bytes: ByteArray, val expected: JSONObject, val key: JSONObject = expected.getJSONObject("signingKey"))
}
