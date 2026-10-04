package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject

/** Validates downloaded evidence before any receiving-device state is created. */
object HandoverPackageVerifier {
    fun verify(manifest: JSONObject, original: ByteArray, session: JSONObject): LocalHandoverStage {
        check(original.isNotEmpty() && original.size <= 32 * 1024 * 1024)
        val key = session.getJSONObject("signingKey")
        val caseId = manifest.getString("caseId")
        val status = manifest.getString("status")
        check(status == "TARGET_PREPARED" || status == "ACTIVATED") { "接收机准备状态已变化" }
        val stop = HandoverReceiptVerifier.verify(manifest.getJSONObject("stopReceipt"), key,
            "acrylic-pda-handover-stop", "acrylic-outbound")
        val approval = HandoverReceiptVerifier.verify(manifest.getJSONObject("approvalReceipt"), key,
            "acrylic-outbound-handover-receipt", "acrylic-outbound-server")
        val preparation = HandoverReceiptVerifier.verify(manifest.getJSONObject("preparationReceipt"), key,
            "acrylic-outbound-handover-receipt", "acrylic-outbound-server")
        verifyMilestones(caseId, stop, approval, preparation)
        verifyActivation(caseId, status, manifest, key, stop, session)
        val ticket = manifest.getJSONObject("preparedTicket")
        val ticketSha = JwtVerifier.sha(SubmissionJson.canonical(ticket).toByteArray(Charsets.UTF_8))
        check(ticketSha == manifest.getString("preparedTicketSha256") &&
            ticketSha == preparation.getJSONObject("businessDecision").getString("preparedTicketSha256"))
        verifyTarget(caseId, ticket, approval, preparation, stop, manifest, session)
        val body = JSONObject(original.toString(Charsets.UTF_8))
        check(SubmissionJson.canonical(body).toByteArray(Charsets.UTF_8).contentEquals(original)) { "原恢复包不是固定正文" }
        val hash = JwtVerifier.sha(original)
        val source = verifyOrigin(caseId, manifest, stop, body, hash)
        return LocalHandoverStage(caseId, source.getString("assignmentId"), body.getString("orderId"),
            session.getString("deviceId"), session.getString("userId"), session.getString("warehouseId"),
            manifest.getString("inputHighWatermark").toLong(), hash, SubmissionJson.canonical(manifest),
            SubmissionJson.canonical(key), original.clone())
    }

    private fun verifyActivation(caseId: String, status: String, manifest: JSONObject, key: JSONObject,
        stop: JSONObject, session: JSONObject) {
        if (status == "TARGET_PREPARED") {
            check(!manifest.has("activationReceipt")) { "准备阶段包含激活回执" }
            return
        }
        val activation = HandoverReceiptVerifier.verify(manifest.getJSONObject("activationReceipt"), key,
            "acrylic-outbound-handover-receipt", "acrylic-outbound-server")
        val decision = activation.getJSONObject("businessDecision")
        check(activation.getString("commandType") == "ACTIVATE" && activation.getString("status") == "RECORDED" &&
            decision.getString("status") == "ACTIVATED" && decision.getString("caseId") == caseId)
        val context = stop.getJSONObject("caseContext")
        val activatedContext = activation.getJSONObject("caseContext")
        for (field in listOf("caseId", "warehouseId", "scopeId", "submissionId", "canonicalOrderId",
            "sourceKind", "fromWorkRound", "toWorkRound"))
            check(activatedContext.getString(field) == context.getString(field))
        check(decision.getString("targetDeviceId") == session.getString("deviceId") &&
            decision.getString("targetUserId") == session.getString("userId") &&
            decision.getString("workRound") == context.getString("toWorkRound") &&
            decision.getString("startOrderScanSeq").toLong() == manifest.getString("inputHighWatermark").toLong() + 1)
    }

    private fun verifyMilestones(caseId: String, stop: JSONObject, approval: JSONObject, preparation: JSONObject) {
        check(approval.getString("commandType") == "APPROVE" && approval.getString("status") == "RECORDED" &&
            approval.getJSONObject("businessDecision").getString("status") == "APPROVED" &&
            preparation.getString("commandType") == "PREPARE" && preparation.getString("status") == "RECORDED" &&
            preparation.getJSONObject("businessDecision").getString("status") == "TARGET_PREPARED")
        val origin = stop.getJSONObject("caseContext")
        check(origin.getString("caseId") == caseId && origin.getString("sourceKind") == "FORMAL")
        val fields = listOf("warehouseId", "scopeId", "submissionId", "canonicalOrderId", "sourceKind",
            "fromWorkRound", "toWorkRound")
        for (receipt in listOf(approval, preparation)) {
            val context = receipt.getJSONObject("caseContext")
            check(context.getString("caseId") == caseId &&
                receipt.getJSONObject("businessDecision").getString("caseId") == caseId)
            for (field in fields) check(context.getString(field) == origin.getString(field))
        }
    }

    private fun verifyTarget(caseId: String, ticket: JSONObject, approval: JSONObject, preparation: JSONObject,
        stop: JSONObject, manifest: JSONObject, session: JSONObject) {
        val decision = preparation.getJSONObject("businessDecision")
        for ((field, sessionField) in listOf("targetDeviceId" to "deviceId", "targetUserId" to "userId"))
            check(ticket.getString(field) == session.getString(sessionField) && decision.getString(field) == ticket.getString(field))
        val context = stop.getJSONObject("caseContext")
        val high = manifest.getString("inputHighWatermark")
        check(ticket.getString("caseId") == caseId && ticket.getString("issuedAt") == preparation.getString("recordedAt") &&
            ticket.getString("canonicalOrderId") == context.getString("canonicalOrderId") &&
            ticket.getString("inputHighWatermark") == high &&
            approval.getJSONObject("businessDecision").getString("inputHighWatermark") == high &&
            context.getString("warehouseId") == session.getString("warehouseId"))
    }

    private fun verifyOrigin(caseId: String, manifest: JSONObject, stop: JSONObject, body: JSONObject,
        hash: String): JSONObject {
        val command = stop.getJSONObject("originalCommand").getJSONObject("command")
        val source = stop.getJSONObject("source")
        val context = stop.getJSONObject("caseContext")
        val actor = stop.getJSONObject("actor")
        check(stop.getString("status") == "STOPPED" && stop.getJSONObject("originalCommand").getString("caseId") == caseId &&
            manifest.getString("recoveryPackageSha256") == hash && command.getString("payloadSha256") == hash)
        check(command.getString("recoveryPackageId") == body.getString("recoveryPackageId") &&
            command.getString("changeRequestId") == body.getString("changeRequestId"))
        check(command.getString("captureHighWatermark") == body.getString("captureHighWatermark") &&
            command.getString("captureHighWatermark") == manifest.getString("inputHighWatermark"))
        check(source.getString("assignmentId") == body.getString("assignmentId") &&
            source.getString("orderId") == body.getString("orderId") &&
            source.getString("deviceId") == body.getString("freezeDeviceId") &&
            source.getString("sourceRecoveryEpoch") == body.getString("sourceRecoveryEpoch") &&
            source.getString("workRound") == body.getString("workRound"))
        for (field in listOf("warehouseId", "scopeId", "submissionId"))
            check(source.getString(field) == context.getString(field))
        check(source.getString("orderId") == context.getString("canonicalOrderId") &&
            source.getString("workRound") == context.getString("fromWorkRound") &&
            source.getString("deviceId") == actor.getString("deviceId") &&
            source.getString("operatorId") == actor.getString("principalId"))
        return source
    }
}
