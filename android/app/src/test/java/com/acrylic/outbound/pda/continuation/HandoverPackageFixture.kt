package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import java.security.KeyPairGenerator
import java.security.Signature
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
    private val sourceDevice = UUID.randomUUID().toString()
    private val sourceUser = UUID.randomUUID().toString()
    private val targetDevice = UUID.randomUUID().toString()
    val targetUser = UUID.randomUUID().toString()
    private val packageId = UUID.randomUUID().toString()
    private val recallId = UUID.randomUUID().toString()
    val key = JSONObject().put("algorithm", "RS256").put("keyId", "test-key")
        .put("publicKey", Base64.getEncoder().encodeToString(pair.public.encoded))
    val session = JSONObject().put("deviceId", targetDevice).put("userId", targetUser)
        .put("warehouseId", warehouse).put("signingKey", key)

    fun packageBytes(activated: Boolean = false): Pair<JSONObject, ByteArray> {
        val body = JSONObject().put("recoveryPackageId", packageId).put("changeRequestId", recallId)
            .put("assignmentId", assignment).put("orderId", order).put("freezeDeviceId", sourceDevice)
            .put("sourceRecoveryEpoch", "1").put("workRound", "1").put("captureHighWatermark", "7")
        val bytes = SubmissionJson.canonical(body).toByteArray()
        val context = JSONObject().put("caseId", caseId).put("warehouseId", warehouse).put("scopeId", scope)
            .put("submissionId", submission).put("canonicalOrderId", order).put("sourceKind", "FORMAL")
            .put("fromWorkRound", "1").put("toWorkRound", "2")
        val source = JSONObject().put("warehouseId", warehouse).put("scopeId", scope)
            .put("submissionId", submission).put("orderId", order).put("assignmentId", assignment)
            .put("deviceId", sourceDevice).put("operatorId", sourceUser)
            .put("sourceRecoveryEpoch", "1").put("workRound", "1")
        val command = JSONObject().put("payloadSha256", JwtVerifier.sha(bytes))
            .put("recoveryPackageId", packageId).put("changeRequestId", recallId).put("captureHighWatermark", "7")
        val stop = JSONObject().put("status", "STOPPED").put("caseContext", context).put("source", source)
            .put("actor", JSONObject().put("deviceId", sourceDevice).put("principalId", sourceUser))
            .put("originalCommand", JSONObject().put("caseId", caseId).put("command", command))
        val approval = JSONObject().put("status", "RECORDED").put("commandType", "APPROVE")
            .put("caseContext", context).put("businessDecision", JSONObject().put("caseId", caseId)
                .put("status", "APPROVED").put("inputHighWatermark", "7"))
        val ticket = JSONObject().put("caseId", caseId).put("targetDeviceId", targetDevice)
            .put("targetUserId", targetUser).put("canonicalOrderId", order).put("inputHighWatermark", "7")
            .put("issuedAt", "2026-10-04T00:00:00Z")
        val ticketSha = JwtVerifier.sha(SubmissionJson.canonical(ticket).toByteArray())
        val preparation = JSONObject().put("status", "RECORDED").put("commandType", "PREPARE")
            .put("caseContext", context).put("recordedAt", ticket.getString("issuedAt"))
            .put("businessDecision", JSONObject().put("caseId", caseId).put("status", "TARGET_PREPARED")
                .put("targetDeviceId", targetDevice).put("targetUserId", targetUser)
                .put("preparedTicketSha256", ticketSha))
        val manifest = JSONObject().put("caseId", caseId).put("status", if (activated) "ACTIVATED" else "TARGET_PREPARED")
            .put("recoveryPackageSha256", JwtVerifier.sha(bytes)).put("inputHighWatermark", "7")
            .put("preparedTicket", ticket).put("preparedTicketSha256", ticketSha)
            .put("stopReceipt", sign(stop, "acrylic-outbound", "acrylic-pda-handover-stop"))
            .put("approvalReceipt", sign(approval, "acrylic-outbound-server", "acrylic-outbound-handover-receipt"))
            .put("preparationReceipt", sign(preparation, "acrylic-outbound-server", "acrylic-outbound-handover-receipt"))
        if (activated) {
            val decision = JSONObject().put("caseId", caseId).put("status", "ACTIVATED")
                .put("targetDeviceId", targetDevice).put("targetUserId", targetUser)
                .put("workRound", "2").put("startOrderScanSeq", "8")
            manifest.put("activationReceipt", activation(context, decision))
        }
        return manifest to bytes
    }

    fun activation(context: JSONObject, decision: JSONObject): JSONObject {
        val receipt = JSONObject().put("status", "RECORDED").put("commandType", "ACTIVATE")
            .put("caseContext", context).put("businessDecision", decision)
        return sign(receipt, "acrylic-outbound-server", "acrylic-outbound-handover-receipt")
    }

    private fun sign(receipt: JSONObject, issuer: String, audience: String): JSONObject {
        val flags = JSONObject().put("writeConfirmationAvailable", false).put("releaseAllowed", false)
            .put("mayResumeScanning", false).put("stockReleased", false).put("localDeletionAllowed", false)
        receipt.put("capabilities", flags)
        val header = JSONObject().put("alg", "RS256").put("typ", "JWT").put("kid", "test-key")
        val claims = JSONObject().put("iss", issuer).put("aud", audience).put("receipt", receipt)
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val input = encoder.encodeToString(SubmissionJson.canonical(header).toByteArray()) + "." +
            encoder.encodeToString(SubmissionJson.canonical(claims).toByteArray())
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(pair.private); update(input.toByteArray(Charsets.US_ASCII))
        }.sign()
        return receipt.put("signedReceipt", input + "." + encoder.encodeToString(signature))
    }
}
