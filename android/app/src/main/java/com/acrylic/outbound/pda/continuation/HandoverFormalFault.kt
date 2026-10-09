package com.acrylic.outbound.pda.continuation

import org.json.JSONObject
import java.time.Instant

/** Consumes supervisor custody evidence without treating it as a source-device STOP acknowledgement. */
internal object HandoverFormalFault {
    fun verify(manifest: JSONObject, key: JSONObject): JSONObject {
        check(!manifest.has("stopReceipt") && manifest.getString("stopKind") == "FAULT_FENCE")
        val receipt = HandoverReceiptVerifier.verify(manifest.getJSONObject("faultReceipt"), key,
            "acrylic-outbound-handover-receipt", "acrylic-outbound-server")
        val decision = receipt.getJSONObject("businessDecision")
        val context = receipt.getJSONObject("caseContext")
        check(receipt.getString("commandType") == "FAULT_FENCE" && receipt.getString("status") == "RECORDED" &&
            decision.getString("status") == "FAULT_FENCED" && decision.getString("stopKind") == "FAULT_FENCE" &&
            decision.get("oldDeviceAcknowledged") == false && context.getString("stopState") == "REQUESTED" &&
            context.getString("sourceKind") == "FORMAL" && decision.getString("caseId") == manifest.getString("caseId"))
        val source = decision.getJSONObject("source")
        check(source.getString("sourceKind") == "FORMAL" &&
            source.getString("deviceId") == decision.getString("sourceDeviceId") &&
            decision.getString("disabledDeviceVersion").matches(Regex("[1-9][0-9]*")))
        supervisor(receipt, source, context)
        custody(decision.getJSONObject("physicalControl"), Instant.parse(receipt.getString("recordedAt")))
        return receipt
    }

    fun origin(manifest: JSONObject, receipt: JSONObject, body: JSONObject, hash: String): JSONObject {
        val decision = receipt.getJSONObject("businessDecision")
        val archive = decision.getJSONObject("archivedEvidence")
        val source = decision.getJSONObject("source")
        val context = receipt.getJSONObject("caseContext")
        check(archive.getString("sourceKind") == "FORMAL" && archive.getString("status") == "VERIFIED_COMPLETE_AT_STOP" &&
            archive.get("mayResumeScanning") == false && archive.get("stockReleased") == false &&
            archive.getString("payloadSha256") == hash && decision.getString("payloadSha256") == hash &&
            manifest.getString("recoveryPackageSha256") == hash)
        for (field in listOf("changeRequestId", "recoveryPackageId", "captureHighWatermark"))
            check(archive.getString(field) == body.getString(field))
        check(archive.getString("orderScanHighWatermark") == manifest.getString("inputHighWatermark") &&
            archive.getString("assignmentId") == source.getString("assignmentId") &&
            archive.getString("submissionId") == context.getString("submissionId") &&
            archive.getString("workRound") == source.getString("workRound"))
        source(source, context, body)
        audit(archive, source, body)
        return source
    }

    private fun source(source: JSONObject, context: JSONObject, body: JSONObject) {
        for (field in listOf("assignmentId", "orderId", "sourceRecoveryEpoch", "workRound"))
            check(source.getString(field) == body.getString(field))
        for (field in listOf("warehouseId", "scopeId", "submissionId"))
            check(source.getString(field) == context.getString(field))
        check(source.getString("deviceId") == body.getString("freezeDeviceId") &&
            source.getString("orderId") == context.getString("canonicalOrderId") &&
            source.getString("workRound") == context.getString("fromWorkRound"))
    }

    private fun supervisor(receipt: JSONObject, source: JSONObject, context: JSONObject) {
        val actor = receipt.getJSONObject("actor")
        check(actor.isNull("deviceId") && actor.isNull("sessionId") &&
            actor.getString("principalId") != source.getString("operatorId") &&
            actor.getString("principalId") != context.getString("requestedBy") &&
            actor.getString("warehouseId") == context.getString("warehouseId"))
        val permissions = actor.getJSONObject("permissionSnapshot").getJSONArray("effectivePermissions")
        check((0 until permissions.length()).any { permissions.getString(it) == "order:handover" })
    }

    private fun custody(control: JSONObject, recorded: Instant) {
        check(control.getString("deviceState") == "POWERED_OFF_IN_CUSTODY" && control.getString("unresolvedInputCount") == "0" &&
            !Instant.parse(control.getString("controlledAt")).isAfter(recorded))
        for (field in listOf("deviceLocation", "goodsLocation")) {
            val location = control.getString(field)
            check(location.isNotBlank() && location.length <= 200 && location.none { Character.isISOControl(it) })
        }
    }

    private fun audit(archive: JSONObject, source: JSONObject, body: JSONObject) {
        val coverage = body.getJSONArray("auditCoverage")
        check(coverage.length() == 1)
        val item = coverage.getJSONObject(0)
        check(item.getString("originDeviceId") == source.getString("deviceId") &&
            item.getString("throughSeq") == archive.getString("auditThroughSeq") &&
            archive.getString("auditThroughSeq").toLong() > 0)
    }
}
