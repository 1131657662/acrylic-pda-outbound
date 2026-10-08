package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.continuation.HandoverReceiptVerifier
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject

/** Verifies the signed milestone chain before consuming any receiving qualification. */
internal object TemporaryHandoverReceipts {
    data class Verified(val stop: JSONObject, val preparation: JSONObject, val qualification: JSONObject, val fault: Boolean = false)

    fun verify(manifest: JSONObject, session: JSONObject): Verified {
        check(manifest.getString("sourceKind") == "TEMP" && manifest.getString("packageKind") == "TEMP_FROZEN_PAYLOAD")
        check(manifest.getString("status") == "TARGET_PREPARED" && !manifest.has("activationReceipt")) {
            "临时交接尚未支持此接收状态，请保留原资料"
        }
        val key = session.getJSONObject("signingKey")
        val fault = manifest.optString("stopKind") == "FAULT_FENCE"
        if (!fault) check(!manifest.has("faultReceipt") && !manifest.has("stopKind"))
        val stop = if (fault) TemporaryHandoverFault.verify(manifest, key)
            else HandoverReceiptVerifier.verify(manifest.getJSONObject("stopReceipt"), key,
                "acrylic-pda-handover-stop", "acrylic-outbound")
        val approval = command(manifest.getJSONObject("approvalReceipt"), key, "APPROVE", "APPROVED")
        val preparation = command(manifest.getJSONObject("preparationReceipt"), key, "PREPARE", "TARGET_PREPARED")
        contexts(manifest, stop, listOf(approval, preparation), fault)
        target(manifest, session, preparation)
        check(approval.getJSONObject("businessDecision").getString("inputHighWatermark") == manifest.getString("inputHighWatermark"))
        val qualification = manifest.getJSONObject("preparedTicket").getJSONObject("temporaryQualification")
        check(SubmissionJson.canonical(qualification) == SubmissionJson.canonical(
            preparation.getJSONObject("businessDecision").getJSONObject("temporaryQualification")))
        for (field in listOf("sourceKind", "scopeId", "fromWorkRound", "toWorkRound"))
            check(qualification.getString(field) == stop.getJSONObject("caseContext").getString(field))
        val context = preparation.getJSONObject("caseContext")
        check(context.getString("originalResultState") == "ABORTED" && context.getString("stopState") ==
            (if (fault) "FAULT_FENCED" else "DEVICE_ACKED") &&
            context.getString("integrityState") == "COMPLETE" && context.getString("physicalState") == "MATCHED")
        capabilities(listOf(manifest, stop, approval, preparation))
        return Verified(stop, preparation, qualification, fault)
    }

    private fun capabilities(receipts: List<JSONObject>) {
        for (receipt in receipts) {
            val flags = receipt.getJSONObject("capabilities")
            for (field in listOf("writeConfirmationAvailable", "releaseAllowed", "mayResumeScanning", "stockReleased", "localDeletionAllowed"))
                check(flags.get(field) == false) { "临时交接不能授予作业或删除能力" }
        }
    }

    private fun command(receipt: JSONObject, key: JSONObject, kind: String, status: String): JSONObject {
        val result = HandoverReceiptVerifier.verify(receipt, key, "acrylic-outbound-handover-receipt", "acrylic-outbound-server")
        check(result.getString("commandType") == kind && result.getString("status") == "RECORDED" &&
            result.getJSONObject("businessDecision").getString("status") == status)
        return result
    }

    private fun contexts(manifest: JSONObject, stop: JSONObject, commands: List<JSONObject>, fault: Boolean) {
        val context = stop.getJSONObject("caseContext")
        val id = TemporaryVerifier.uuid(manifest.getString("caseId"))
        check((fault || stop.getString("status") == "STOPPED") && context.getString("sourceKind") == "TEMP" && context.getString("caseId") == id)
        for (field in listOf("warehouseId", "scopeId", "submissionId", "canonicalOrderId")) TemporaryVerifier.uuid(context.getString(field))
        check(Math.addExact(TemporaryVerifier.positive(context.getString("fromWorkRound")), 1) ==
            TemporaryVerifier.positive(context.getString("toWorkRound")))
        for (receipt in commands) {
            check(receipt.getJSONObject("businessDecision").getString("caseId") == id)
            for (field in listOf("caseId", "warehouseId", "scopeId", "submissionId", "canonicalOrderId", "sourceKind", "fromWorkRound", "toWorkRound"))
                check(receipt.getJSONObject("caseContext").getString(field) == context.getString(field))
        }
    }

    private fun target(manifest: JSONObject, session: JSONObject, preparation: JSONObject) {
        val ticket = manifest.getJSONObject("preparedTicket")
        val decision = preparation.getJSONObject("businessDecision")
        val sha = JwtVerifier.sha(SubmissionJson.canonical(ticket).toByteArray(Charsets.UTF_8))
        check(sha == manifest.getString("preparedTicketSha256") && sha == decision.getString("preparedTicketSha256"))
        for ((field, identity) in listOf("targetUserId" to "userId", "targetDeviceId" to "deviceId"))
            check(ticket.getString(field) == session.getString(identity) && decision.getString(field) == ticket.getString(field))
        val context = preparation.getJSONObject("caseContext")
        for (field in listOf("caseId", "canonicalOrderId")) check(ticket.getString(field) == context.getString(field))
        check(context.getString("warehouseId") == session.getString("warehouseId") &&
            ticket.getString("inputHighWatermark") == manifest.getString("inputHighWatermark") &&
            ticket.getString("issuedAt") == preparation.getString("recordedAt"))
    }
}
