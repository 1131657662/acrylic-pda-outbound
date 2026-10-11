package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import java.util.UUID

/** One canonical explicit intent binds source/target epochs, admitted LOGIN and the retained original bytes. */
internal object TemporaryRecoveredConfirmationCommand {
    fun create(stage: TemporaryRecoveredHandoverStage, manifest: JSONObject): TemporaryRecoveredConfirmation {
        val command = body(stage, manifest, UUID.randomUUID().toString())
        val original = SubmissionJson.canonical(command)
        return TemporaryRecoveredConfirmation(stage.caseId, command.getString("commandId"), original,
            JwtVerifier.sha(original.toByteArray(Charsets.UTF_8)))
    }
    fun require(row: TemporaryRecoveredConfirmation, stage: TemporaryRecoveredHandoverStage, manifest: JSONObject): ByteArray {
        check(row.caseId == stage.caseId && row.state in setOf("PENDING", "ACTION_REQUIRED", "CONFIRMED"))
        TemporaryVerifier.uuid(row.commandId)
        val expected = SubmissionJson.canonical(body(stage, manifest, row.commandId))
        check(row.commandJson == expected) { "恢复确认原意图或资料变化，请保留原请求" }
        val bytes = row.commandJson.toByteArray(Charsets.UTF_8)
        check(bytes.size in 1..65536 && row.commandSha256 == JwtVerifier.sha(bytes)) { "恢复确认原请求摘要变化" }
        return bytes
    }
    private fun body(stage: TemporaryRecoveredHandoverStage, manifest: JSONObject, commandId: String): JSONObject {
        check(manifest.getString("caseId") == stage.caseId && stage.sourceRecoveryEpoch != stage.targetRecoveryEpoch)
        return JSONObject().put("commandVersion", 1).put("commandType", "RECOVERED_TEMP_HANDOVER_CONFIRM")
            .put("commandId", commandId).put("caseId", stage.caseId).put("qualificationId", stage.qualificationId)
            .put("transportRecoveryEpoch", stage.targetRecoveryEpoch).put("sourceRecoveryEpoch", stage.sourceRecoveryEpoch)
            .put("expectedCaseVersion", "4").put("qualificationSha256", stage.qualificationSha256)
            .put("recipientSessionSha256", stage.recipientSessionSha256).put("manifestSha256", stage.manifestSha256)
            .put("preparedTicketSha256", manifest.getString("preparedTicketSha256")).put("payloadSha256", stage.payloadSha256)
            .put("checklistSha256", JwtVerifier.sha(SubmissionJson.canonical(manifest.getJSONArray("checklist")).toByteArray(Charsets.UTF_8)))
            .put("physicalConfirmed", true)
    }
}
