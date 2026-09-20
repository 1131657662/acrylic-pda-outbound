package com.acrylic.outbound.pda.retention

import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import java.security.*
import java.util.Base64

internal class AuditProofFactory {
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val key = JSONObject().put("algorithm", "RS256").put("keyId", "audit-test")
        .put("publicKey", Base64.getEncoder().encodeToString(keys.public.encoded))

    fun proof(payload: JSONObject, warehouse: String, epoch: String = "1"): JSONObject = sign(JSONObject()
        .put("archiveVersion", 1).put("originDeviceId", payload.getString("originDeviceId")).put("warehouseId", warehouse)
        .put("recoveryEpoch", epoch).put("afterSeq", payload.getString("afterSeq")).put("throughSeq", payload.getString("throughSeq"))
        .put("payloadSha256", AuditArchiveVerifier.digest(payload))
        .put("byteCount", SubmissionJson.canonical(payload).toByteArray(Charsets.UTF_8).size.toString())
        .put("archivedAt", "2026-09-19T00:00:00Z").put("retainUntil", "2031-09-19T00:00:00Z")
        .put("storagePolicy", "SERVER_DURABLE_FILES_V1").put("localDeletionAllowed", false))

    fun sign(proof: JSONObject, audience: String = "acrylic-pda-audit-archive", algorithm: String = "RS256"): JSONObject {
        val result = JSONObject(proof.toString()).apply { remove("signedArchive") }
        val header = JSONObject().put("alg", algorithm).put("typ", "JWT").put("kid", "audit-test")
        val claims = JSONObject().put("iss", "acrylic-outbound").put("aud", audience).put("archive", JSONObject(result.toString()))
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val raw = encoder.encodeToString(header.toString().toByteArray()) + "." + encoder.encodeToString(claims.toString().toByteArray())
        val signer = Signature.getInstance("SHA256withRSA").apply { initSign(keys.private); update(raw.toByteArray(Charsets.US_ASCII)) }
        return result.put("signedArchive", "$raw.${encoder.encodeToString(signer.sign())}")
    }

    fun row(proof: JSONObject) = AuditArchive(proof.getString("recoveryEpoch"), proof.getString("originDeviceId"),
        proof.getString("afterSeq").toLong(), proof.getString("throughSeq").toLong(), proof.getString("payloadSha256"), "{}",
        proofJson = proof.toString(), signingKeyJson = key.toString())
}
