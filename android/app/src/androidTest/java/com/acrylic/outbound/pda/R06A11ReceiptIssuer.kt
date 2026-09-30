package com.acrylic.outbound.pda

import com.acrylic.outbound.pda.evidence.LocalDraftReceiptVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

/** Synthetic peer receipt issuer; its private key exists only in this test process. */
internal class R06A11ReceiptIssuer {
    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val key = JSONObject().put("algorithm", "RS256").put("keyId", "r06a11-device-test")
        .put("publicKey", Base64.getEncoder().encodeToString(pair.public.encoded))
    var change: (JSONObject) -> Unit = {}

    fun response(facts: LocalDraftReceiptVerifier.Expected): ByteArray {
        val stop = facts.stop
        val request = facts.request
        val draft = JSONObject(stop.metadataJson).getJSONObject("draft")
        val receipt = JSONObject().put("receiptVersion", 1).put("operationKind", request.operation)
            .put("originalKey", request.originalKey).put("evidenceId", stop.evidenceId)
            .put("clientOrderId", stop.clientOrderId).put("warehouseId", draft.getString("warehouseId"))
            .put("creatorDeviceId", draft.getString("creatorDeviceId")).put("originalOperatorId", draft.getString("operatorId"))
            .put("rootSha256", stop.rootSha256).put("requestSha256", request.requestSha256)
            .put("recordedAt", "2026-09-30T00:00:00Z").put("businessFactsStatus", "NOT_EVALUATED")
            .put("status", if (request.operation == "VERIFY") "ORIGINALS_STORED" else "RECEIVED")
            .put("localDeletionAllowed", false).put("releaseAllowed", false)
            .put("mayResumeScanning", false).put("writeConfirmationAvailable", false)
            .put("detail", detail(facts))
        change(receipt)
        val claims = JSONObject().put("iss", "acrylic-outbound").put("aud", LocalDraftReceiptVerifier.AUDIENCE)
            .put("receipt", JSONObject(receipt.toString()))
        receipt.put("signedReceipt", sign(claims))
        return SubmissionJson.canonical(receipt).toByteArray(Charsets.UTF_8)
    }

    private fun detail(facts: LocalDraftReceiptVerifier.Expected): JSONObject = when (facts.request.operation) {
        "OPEN" -> JSONObject()
        "ITEM" -> JSONObject().put("ordinal", facts.request.position)
            .put("bytesSha256", JSONObject(facts.request.requestJson).getString("bytesSha256"))
        "VERIFY" -> JSONObject().put("recordCount", facts.stop.recordCount).put("byteCount", facts.stop.totalBytes)
            .put("ownCaptureCount", facts.counts.own).put("otherCaptureCount", facts.counts.other)
            .put("archiveCount", facts.counts.archives)
        else -> error("Unknown fixture operation")
    }

    private fun sign(claims: JSONObject): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = JSONObject().put("alg", "RS256").put("typ", "JWT").put("kid", key.getString("keyId"))
        val input = encoder.encodeToString(header.toString().toByteArray()) + "." +
            encoder.encodeToString(claims.toString().toByteArray())
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(pair.private)
        signature.update(input.toByteArray(Charsets.US_ASCII))
        return input + "." + encoder.encodeToString(signature.sign())
    }
}
