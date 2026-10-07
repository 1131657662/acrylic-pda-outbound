package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.continuation.HandoverImportSigning
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Generated milestone signatures and HTTP boundary only; the recipient uses real Room and downloaded parts. */
internal class TemporaryHandoverFixture(root: File) : AutoCloseable {
    val local = TemporaryFixture(root)
    val signer = HandoverImportSigning()
    val db get() = local.db
    val caseId = id()
    val scopeId = id()
    val submissionId = id()
    val sourceDevice = id()
    val sourceUser = id()
    val sourceAuthorization = id()
    val order = id()
    val sourceClient = id()
    val prepared = local.repository.prepare {}
    val session get() = checkNotNull(local.auth)
    var body = body()
    var manifest = manifest()
    var beforeOriginal: (String) -> Unit = {}
    var originals = 0
    val connection = object : TemporaryConnection by local {
        override fun original(request: TemporaryConnection.Original): ByteArray {
            originals++; beforeOriginal(request.path)
            return if (request.path.endsWith("/manifest")) bytes(manifest) else bytes(body)
        }
    }
    val repository get() = TemporaryHandoverRepository(db, connection)
    init { session.put("signingKey", signer.key) }

    fun resnapshot() { manifest = manifest() }
    fun resign(field: String) { manifest.put(field, signer.receipt(manifest.getJSONObject(field).apply { remove("signedReceipt") }, field == "stopReceipt")) }
    private fun body(): JSONObject {
        val source = JSONObject().put("kind", "TEMP").put("clientOrderId", sourceClient).put("creatorDeviceId", sourceDevice)
        val plan = JSONObject().put("clientOrderId", sourceClient).put("creatorDeviceId", sourceDevice)
            .put("authorizationId", sourceAuthorization).put("revision", "1").put("vehicleNo", "合成车")
            .put("containerNo", "合成柜").put("reason", "测试交接").put("lines", JSONArray())
        return JSONObject().put("schemaVersion", 2).put("source", source).put("temporaryOrder", plan)
            .put("assignmentId", JSONObject.NULL).put("submissionId", submissionId).put("workRound", "1")
            .put("authorizationId", sourceAuthorization).put("freezeDeviceId", sourceDevice)
            .put("sourceRecoveryEpoch", session.getString("serverRecoveryEpoch"))
            .put("inventorySnapshotId", local.manifest.getString("inventorySnapshotId"))
            .put("policyVersion", local.manifest.getString("policyVersion")).put("planSha256", sha(plan))
            .put("captureHighWatermark", "8").put("captures", JSONArray().put(JSONObject().put("orderScanSeq", "1")))
    }
    private fun context() = JSONObject().put("caseId", caseId).put("sourceKind", "TEMP").put("caseVersion", "4")
        .put("warehouseId", session.getString("warehouseId")).put("scopeId", scopeId).put("submissionId", submissionId)
        .put("canonicalOrderId", order).put("fromWorkRound", "1").put("toWorkRound", "2")
        .put("originalResultState", "ABORTED").put("stopState", "DEVICE_ACKED")
        .put("integrityState", "COMPLETE").put("physicalState", "MATCHED")

    private fun qualification() = JSONObject().put("authorizationId", prepared.authorizationId)
        .put("manifestSha256", prepared.manifestSha256).put("expiresAt", local.manifest.getString("expiresAt"))
        .put("serverRecoveryEpoch", session.getString("serverRecoveryEpoch"))
        .put("userAuthVersion", session.getString("userAuthVersion")).put("deviceVersion", session.getString("deviceVersion"))
        .put("sourceKind", "TEMP").put("scopeId", scopeId).put("mappingVersion", "1")
        .put("fromWorkRound", "1").put("toWorkRound", "2").put("planSha256", body.getString("planSha256"))
        .put("payloadSha256", sha(body))

    private fun stop(): JSONObject {
        val source = JSONObject().put("sourceKind", "TEMP").put("assignmentId", JSONObject.NULL)
            .put("warehouseId", session.getString("warehouseId")).put("scopeId", scopeId).put("submissionId", submissionId)
            .put("orderId", order).put("workRound", "1").put("authorizationId", sourceAuthorization)
            .put("deviceId", sourceDevice).put("operatorId", sourceUser).put("creatorDeviceId", sourceDevice)
            .put("clientOrderId", sourceClient).put("sourceRecoveryEpoch", session.getString("serverRecoveryEpoch"))
        val command = JSONObject().put("sourceKind", "TEMP").put("sourceEvidenceSetId", id()).put("verificationId", id())
            .put("setSha256", "a".repeat(64)).put("captureHighWatermark", "9").put("orderScanHighWatermark", "1").put("auditThroughSeq", "20")
        val proof = JSONObject(command.toString()).put("status", "VERIFIED_COMPLETE_AT_STOP").put("mayResumeScanning", false)
            .put("stockReleased", false).put("authorizationId", sourceAuthorization).put("submissionId", submissionId).put("workRound", "1")
        return signer.receipt(JSONObject().put("status", "STOPPED").put("caseContext", context()).put("source", source)
            .put("actor", JSONObject().put("deviceId", sourceDevice).put("principalId", sourceUser))
            .put("originalCommand", JSONObject().put("caseId", caseId).put("command", command)).put("temporaryStop", proof), true)
    }

    private fun manifest(): JSONObject {
        val ticket = JSONObject().put("caseId", caseId).put("canonicalOrderId", order).put("inputHighWatermark", "1")
            .put("targetDeviceId", session.getString("deviceId")).put("targetUserId", session.getString("userId"))
            .put("issuedAt", local.time.toString()).put("temporaryQualification", qualification())
        val approval = command("APPROVE", JSONObject().put("caseId", caseId).put("status", "APPROVED").put("inputHighWatermark", "1"))
        val decision = JSONObject().put("caseId", caseId).put("status", "TARGET_PREPARED")
            .put("targetDeviceId", session.getString("deviceId")).put("targetUserId", session.getString("userId"))
            .put("temporaryQualification", qualification()).put("preparedTicketSha256", sha(ticket))
        val preparation = command("PREPARE", decision)
        return JSONObject().put("caseId", caseId).put("sourceKind", "TEMP").put("packageKind", "TEMP_FROZEN_PAYLOAD")
            .put("status", "TARGET_PREPARED").put("inputHighWatermark", "1").put("preparedTicket", ticket)
            .put("preparedTicketSha256", sha(ticket)).put("recoveryPackageSha256", sha(body))
            .put("stopReceipt", stop()).put("approvalReceipt", approval).put("preparationReceipt", preparation)
            .put("capabilities", JSONObject(preparation.getJSONObject("capabilities").toString()))
            .put("checklist", JSONArray())
    }
    private fun command(kind: String, decision: JSONObject) = signer.receipt(JSONObject().put("status", "RECORDED")
        .put("commandType", kind).put("caseContext", context()).put("recordedAt", local.time.toString()).put("businessDecision", decision))
    private fun id() = UUID.randomUUID().toString()
    fun bytes(value: JSONObject) = SubmissionJson.canonical(value).toByteArray(Charsets.UTF_8)
    private fun sha(value: JSONObject) = JwtVerifier.sha(bytes(value))
    override fun close() = local.close()
}
