package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Synthetic supervisor identity and development RSA signature, never original-device evidence. */
internal class HandoverFormalFaultFixture {
    val normal = HandoverPackageFixture()
    val session get() = normal.session

    fun packageBytes(activated: Boolean = false): Pair<JSONObject, ByteArray> {
        val (manifest, original) = normal.packageBytes(activated)
        val stop = manifest.getJSONObject("stopReceipt")
        val source = stop.getJSONObject("source").put("sourceKind", "FORMAL")
        val body = JSONObject(original.toString(Charsets.UTF_8)).put("auditCoverage", JSONArray().put(
            JSONObject().put("originDeviceId", source.getString("deviceId")).put("throughSeq", "31")))
        val bytes = SubmissionJson.canonical(body).toByteArray(Charsets.UTF_8)
        val context = JSONObject(stop.getJSONObject("caseContext").toString()).put("stopState", "REQUESTED")
            .put("requestedBy", UUID.randomUUID().toString())
        val decision = JSONObject().put("caseId", normal.caseId).put("status", "FAULT_FENCED")
            .put("stopKind", "FAULT_FENCE").put("oldDeviceAcknowledged", false)
            .put("sourceDeviceId", source.getString("deviceId")).put("disabledDeviceVersion", "2")
            .put("payloadSha256", JwtVerifier.sha(bytes)).put("source", source)
            .put("archivedEvidence", archive(body, context, bytes)).put("physicalControl", custody())
        val receipt = JSONObject().put("commandType", "FAULT_FENCE").put("status", "RECORDED")
            .put("recordedAt", "2026-10-03T23:59:00Z").put("caseContext", context)
            .put("businessDecision", decision).put("actor", supervisor(context))
        manifest.remove("stopReceipt")
        manifest.put("stopKind", "FAULT_FENCE").put("recoveryPackageSha256", JwtVerifier.sha(bytes))
            .put("faultReceipt", sign(receipt))
        return manifest to bytes
    }

    fun sign(receipt: JSONObject): JSONObject = normal.sign(receipt,
        "acrylic-outbound-server", "acrylic-outbound-handover-receipt")

    private fun archive(body: JSONObject, context: JSONObject, bytes: ByteArray) = JSONObject()
        .put("sourceKind", "FORMAL").put("status", "VERIFIED_COMPLETE_AT_STOP")
        .put("changeRequestId", body.getString("changeRequestId")).put("recoveryPackageId", body.getString("recoveryPackageId"))
        .put("payloadSha256", JwtVerifier.sha(bytes)).put("assignmentId", body.getString("assignmentId"))
        .put("submissionId", context.getString("submissionId")).put("workRound", body.getString("workRound"))
        .put("captureHighWatermark", "7").put("auditThroughSeq", "31").put("orderScanHighWatermark", "7")
        .put("mayResumeScanning", false).put("stockReleased", false)

    private fun custody() = JSONObject().put("deviceState", "POWERED_OFF_IN_CUSTODY")
        .put("controlledAt", "2026-10-03T23:58:00Z").put("deviceLocation", "隔离开发保管柜")
        .put("goodsLocation", "隔离开发货位").put("unresolvedInputCount", "0")

    private fun supervisor(context: JSONObject) = JSONObject().put("principalId", UUID.randomUUID().toString())
        .put("warehouseId", context.getString("warehouseId")).put("deviceId", JSONObject.NULL)
        .put("sessionId", JSONObject.NULL).put("permissionSnapshot", JSONObject()
            .put("effectivePermissions", JSONArray().put("order:handover")))
}
