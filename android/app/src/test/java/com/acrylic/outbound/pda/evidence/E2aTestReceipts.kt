package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.UUID

/** Synthetic server signing boundary. Server-produced originals are checked separately for wire compatibility. */
class E2aTestReceipts {
    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val key = JSONObject().put("algorithm", "RS256").put("keyId", "test-e2a")
        .put("publicKey", Base64.getEncoder().encodeToString(pair.public.encoded))
    private val scope = UUID.randomUUID().toString()
    var change: (JSONObject) -> Unit = {}

    fun receipt(facts: E2aReceiptVerifier.Expected): ByteArray {
        val stop = facts.stop; val request = facts.request
        val result = JSONObject().put("receiptVersion", 1).put("receiptId", UUID.randomUUID().toString())
            .put("operationKind", request.operation).put("originalKey", request.originalKey)
            .put("recordedAt", "2026-09-25T01:00:00Z").put("source", "TEMP").put("sourceId", stop.authorizationId)
            .put("submissionId", stop.submissionId).put("workRound", 1).put("manifestId", stop.manifestId)
            .put("status", if (request.operation == "OPEN") "RECEIVED" else "PARTIAL")
        result.put("originalContext", JSONObject().put("principalId", stop.originalOperatorId)
            .put("originalOperatorId", stop.originalOperatorId).put("warehouseId", stop.warehouseId)
            .put("creatorDeviceId", stop.freezeDeviceId).put("clientOrderId", stop.clientOrderId)
            .put("temporaryAuthorizationId", stop.authorizationId).put("sourceRecoveryEpoch", stop.sourceRecoveryEpoch)
            .put("scopeId", scope).put("currentSessionId", UUID.randomUUID().toString())
            .put("userAuthVersion", "1").put("deviceVersion", "1"))
        val capabilities = JSONObject()
        listOf("writeConfirmationAvailable", "releaseAllowed", "mayResumeScanning", "stockReleased", "localDeletionAllowed")
            .forEach { capabilities.put(it, false) }
        result.put("capabilities", capabilities)
        operation(facts, result); change(result)
        result.put("signedReceipt", sign(JSONObject().put("iss", "acrylic-outbound")
            .put("aud", "acrylic-r06-e2a-intake-v1").put("receipt", JSONObject(result.toString()))))
        return SubmissionJson.canonical(result).toByteArray(Charsets.UTF_8)
    }

    private fun operation(facts: E2aReceiptVerifier.Expected, result: JSONObject) {
        val request = facts.request; val stop = facts.stop; val plan = facts.plan
        val body = JSONObject(request.requestJson)
        if (request.operation != "OPEN") result.put("openOriginalKey", stop.openOriginalKey)
        if (request.operation != "VERIFY") result.put("manifestVersion", 1)
        when (request.operation) {
            "OPEN" -> result.put("snapshotId", stop.snapshotId)
            "PAGE" -> {
                val core = body.getJSONObject("pageCore")
                result.put("snapshotId", stop.snapshotId).put("pageNo", core.getLong("pageNo"))
                    .put("pageCount", core.getLong("pageCount")).put("pageHash", body.getString("pageHash"))
                objectFields(result, core)
            }
            "STOP" -> {
                result.put("manifestBytesHash", plan.manifestBytesHash).put("setHash", plan.setHash)
                    .put("stopEventId", stop.stopEventId)
                objectFields(result, body.getJSONObject("stopProof"))
            }
            "VERIFY" -> result.put("stopOriginalKey", plan.stopOriginalKey).put("setHash", plan.setHash)
                .put("verificationVersion", 1).put("algorithmVersion", 1)
                .put("evidenceSetId", UUID.randomUUID().toString()).put("evidenceVerificationId", UUID.randomUUID().toString())
        }
    }

    private fun objectFields(result: JSONObject, body: JSONObject) {
        val bytes = SubmissionJson.canonical(body).toByteArray(Charsets.UTF_8)
        result.put("objectId", UUID.randomUUID().toString()).put("objectByteCount", bytes.size)
            .put("objectBytesSha256", E2aWire.sha(bytes))
    }

    private fun sign(claims: JSONObject): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = JSONObject().put("alg", "RS256").put("typ", "JWT").put("kid", key.getString("keyId"))
        val text = encoder.encodeToString(header.toString().toByteArray()) + "." +
            encoder.encodeToString(claims.toString().toByteArray())
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(pair.private); signature.update(text.toByteArray(Charsets.US_ASCII))
        return text + "." + encoder.encodeToString(signature.sign())
    }
}
