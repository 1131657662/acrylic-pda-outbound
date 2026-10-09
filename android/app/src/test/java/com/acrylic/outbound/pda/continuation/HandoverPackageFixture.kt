package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Instant
import java.util.Base64
import java.util.UUID

internal class HandoverPackageFixture {
    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val caseId = UUID.randomUUID().toString()
    private val warehouse = UUID.randomUUID().toString()
    private val scope = UUID.randomUUID().toString()
    private val submission = UUID.randomUUID().toString()
    private val order = UUID.randomUUID().toString()
    val assignment = UUID.randomUUID().toString()
    val targetAssignmentId = UUID.randomUUID().toString()
    private val sourceDevice = UUID.randomUUID().toString()
    private val sourceUser = UUID.randomUUID().toString()
    private val targetDevice = UUID.randomUUID().toString()
    val targetUser = UUID.randomUUID().toString()
    private val packageId = UUID.randomUUID().toString()
    private val recallId = UUID.randomUUID().toString()
    private val issuedAt = "2026-10-04T00:00:00Z"
    private val expiresAt = "2026-10-06T00:00:00Z"
    val key = JSONObject().put("algorithm", "RS256").put("keyId", "test-key")
        .put("publicKey", Base64.getEncoder().encodeToString(pair.public.encoded))
    val session = JSONObject().put("deviceId", targetDevice).put("userId", targetUser)
        .put("warehouseId", warehouse).put("signingKey", key).put("serverRecoveryEpoch", "1")
        .put("userAuthVersion", "1").put("deviceVersion", "1")

    fun packageBytes(activated: Boolean = false): Pair<JSONObject, ByteArray> {
        val bytes = SubmissionJson.canonical(recovery()).toByteArray()
        val context = context()
        val ticket = ticket()
        val ticketSha = JwtVerifier.sha(SubmissionJson.canonical(ticket).toByteArray())
        val manifest = JSONObject().put("caseId", caseId).put("status", if (activated) "ACTIVATED" else "TARGET_PREPARED")
            .put("recoveryPackageSha256", JwtVerifier.sha(bytes)).put("inputHighWatermark", "7")
            .put("preparedTicket", ticket).put("preparedTicketSha256", ticketSha)
            .put("stopReceipt", sign(stop(context, bytes), "acrylic-outbound", "acrylic-pda-handover-stop"))
            .put("approvalReceipt", sign(approval(context), "acrylic-outbound-server", "acrylic-outbound-handover-receipt"))
            .put("preparationReceipt", sign(preparation(context, ticketSha), "acrylic-outbound-server",
                "acrylic-outbound-handover-receipt"))
        if (activated) manifest.put("activationReceipt", activation(context, activationDecision()))
        return manifest to bytes
    }

    fun targetAssignment(id: String = targetAssignmentId, orderId: String = order,
        deviceId: String = targetDevice): JSONObject {
        val view = JSONObject().put("id", id).put("orderId", orderId).put("revision", 1)
            .put("deviceId", deviceId).put("operatorId", targetUser).put("epoch", "2")
            .put("serverRecoveryEpoch", "1").put("inventorySnapshotId", UUID.randomUUID().toString())
            .put("planSha256", "plan").put("inventorySha256", "inventory")
            .put("startOrderScanSeq", "8").put("allocationVersion", "0").put("status", "ACTIVE")
            .put("issuedAt", issuedAt).put("expiresAt", expiresAt)
            .put("offlineAuthorizationId", UUID.randomUUID().toString())
        val claims = JSONObject().put("iss", "acrylic-outbound").put("aud", "acrylic-pda-offline")
            .put("sub", targetUser).put("warehouseId", warehouse).put("scope", "pda:handover-import")
            .put("userAuthVersion", "1").put("deviceVersion", "1")
            .put("iat", Instant.parse(issuedAt).epochSecond).put("exp", Instant.parse(expiresAt).epochSecond)
        val fields = mapOf("assignmentId" to "id", "jti" to "offlineAuthorizationId",
            "assignmentEpoch" to "epoch", "deviceId" to "deviceId", "orderId" to "orderId",
            "revision" to "revision", "inventorySnapshotId" to "inventorySnapshotId",
            "serverRecoveryEpoch" to "serverRecoveryEpoch", "planSha256" to "planSha256",
            "inventorySha256" to "inventorySha256", "startOrderScanSeq" to "startOrderScanSeq",
            "allocationVersion" to "allocationVersion")
        fields.forEach { (claim, field) -> claims.put(claim, view.get(field)) }
        return view.put("offlineToken", jwt(claims))
    }

    fun activation(context: JSONObject, decision: JSONObject): JSONObject {
        val receipt = JSONObject().put("status", "RECORDED").put("commandType", "ACTIVATE")
            .put("caseContext", context).put("businessDecision", decision)
        return sign(receipt, "acrylic-outbound-server", "acrylic-outbound-handover-receipt")
    }

    private fun recovery() = JSONObject().put("recoveryPackageId", packageId).put("changeRequestId", recallId)
        .put("assignmentId", assignment).put("orderId", order).put("freezeDeviceId", sourceDevice)
        .put("sourceRecoveryEpoch", "1").put("workRound", "1").put("captureHighWatermark", "7")

    private fun context() = JSONObject().put("caseId", caseId).put("warehouseId", warehouse)
        .put("scopeId", scope).put("submissionId", submission).put("canonicalOrderId", order)
        .put("sourceKind", "FORMAL").put("fromWorkRound", "1").put("toWorkRound", "2")

    private fun ticket() = JSONObject().put("caseId", caseId).put("targetDeviceId", targetDevice)
        .put("targetUserId", targetUser).put("canonicalOrderId", order).put("inputHighWatermark", "7")
        .put("issuedAt", issuedAt)

    private fun stop(context: JSONObject, bytes: ByteArray): JSONObject {
        val source = JSONObject().put("warehouseId", warehouse).put("scopeId", scope)
            .put("submissionId", submission).put("orderId", order).put("assignmentId", assignment)
            .put("deviceId", sourceDevice).put("operatorId", sourceUser)
            .put("sourceRecoveryEpoch", "1").put("workRound", "1")
        val command = JSONObject().put("payloadSha256", JwtVerifier.sha(bytes))
            .put("recoveryPackageId", packageId).put("changeRequestId", recallId).put("captureHighWatermark", "7")
        return JSONObject().put("status", "STOPPED").put("caseContext", context).put("source", source)
            .put("actor", JSONObject().put("deviceId", sourceDevice).put("principalId", sourceUser))
            .put("originalCommand", JSONObject().put("caseId", caseId).put("command", command))
    }

    private fun approval(context: JSONObject) = JSONObject().put("status", "RECORDED")
        .put("commandType", "APPROVE").put("caseContext", context)
        .put("businessDecision", JSONObject().put("caseId", caseId)
            .put("status", "APPROVED").put("inputHighWatermark", "7"))

    private fun preparation(context: JSONObject, ticketSha: String) = JSONObject().put("status", "RECORDED")
        .put("commandType", "PREPARE").put("caseContext", context).put("recordedAt", issuedAt)
        .put("businessDecision", JSONObject().put("caseId", caseId).put("status", "TARGET_PREPARED")
            .put("targetDeviceId", targetDevice).put("targetUserId", targetUser)
            .put("preparedTicketSha256", ticketSha))

    private fun activationDecision() = JSONObject().put("caseId", caseId).put("status", "ACTIVATED")
        .put("assignmentId", targetAssignmentId).put("assignmentEpoch", "2")
        .put("targetDeviceId", targetDevice).put("targetUserId", targetUser)
        .put("workRound", "2").put("startOrderScanSeq", "8")

    fun sign(receipt: JSONObject, issuer: String, audience: String): JSONObject {
        receipt.remove("signedReceipt")
        val flags = JSONObject().put("writeConfirmationAvailable", false).put("releaseAllowed", false)
            .put("mayResumeScanning", false).put("stockReleased", false).put("localDeletionAllowed", false)
        receipt.put("capabilities", flags)
        val claims = JSONObject().put("iss", issuer).put("aud", audience).put("receipt", receipt)
        return receipt.put("signedReceipt", jwt(claims))
    }

    private fun jwt(claims: JSONObject): String {
        val header = JSONObject().put("alg", "RS256").put("typ", "JWT").put("kid", "test-key")
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val input = encoder.encodeToString(SubmissionJson.canonical(header).toByteArray()) + "." +
            encoder.encodeToString(SubmissionJson.canonical(claims).toByteArray())
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(pair.private); update(input.toByteArray(Charsets.US_ASCII))
        }.sign()
        return input + "." + encoder.encodeToString(signature)
    }
}
