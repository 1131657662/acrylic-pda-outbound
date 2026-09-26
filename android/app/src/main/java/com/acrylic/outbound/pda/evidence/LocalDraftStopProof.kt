package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Distinct trust domain for an unsubmitted source; never an existing-S E2a STOP_PROOF. */
object LocalDraftStopProof {
    const val DOMAIN = "ACRYLIC-R06/E2A/LOCAL-DRAFT-STOP/1\n"
    data class Signed(val publicKey: String, val envelope: JSONObject)

    fun core(stop: LocalDraftStop): JSONObject {
        val meta = JSONObject(stop.metadataJson); val draft = meta.getJSONObject("draft")
        check(meta.getString("evidenceId") == stop.evidenceId && meta.getString("kind") == "LOCAL_DRAFT_STOP")
        check(meta.get("schemaVersion") == 1 && draft.getString("clientOrderId") == stop.clientOrderId)
        check(draft.getString("state") in setOf("DRAFT", "SCANNING"))
        check(stop.rootSha256.matches(Regex("[0-9a-f]{64}")) && stop.recordCount in 1..250000 && stop.totalBytes in 1..134217728)
        val result = JSONObject().put("kind", "LOCAL_DRAFT_STOP").put("evidenceId", stop.evidenceId)
            .put("clientOrderId", stop.clientOrderId).put("creatorDeviceId", draft.getString("creatorDeviceId"))
            .put("authorizationId", draft.getString("authorizationId")).put("warehouseId", draft.getString("warehouseId"))
            .put("originalOperatorId", draft.getString("operatorId")).put("snapshotId", meta.getString("snapshotId"))
            .put("stopEventId", meta.getString("stopEventId")).put("stopTransactionVersion", 1)
            .put("metadataSha256", E2aWire.sha(stop.metadataJson.toByteArray(Charsets.UTF_8)))
            .put("rootSha256", stop.rootSha256).put("recordCount", stop.recordCount).put("byteCount", stop.totalBytes)
        for (name in listOf("sourceRecoveryEpoch", "captureHighWatermark", "auditThroughSeq")) {
            val text = meta.getString(name); val number = text.toLong()
            check(text == number.toString() && number in (if (name == "captureHighWatermark") 0L else 1L)..9007199254740991L)
            result.put(name, number)
        }
        listOf("evidenceId", "clientOrderId", "creatorDeviceId", "authorizationId", "warehouseId",
            "originalOperatorId", "snapshotId", "stopEventId").forEach { E2aItemValidation.uuid(result.getString(it)) }
        return result
    }

    fun sign(stop: LocalDraftStop, publicKey: String, sign: (String) -> String): Signed {
        val core = core(stop)
        val envelope = JSONObject().put("schemaVersion", 1).put("algorithm", "ECDSA_P256_SHA256_DER")
            .put("keyId", E2aWire.sha(Base64.getDecoder().decode(publicKey))).put("core", core)
            .put("signature", sign(DOMAIN + SubmissionJson.canonical(core)))
        verify(stop, envelope, publicKey)
        return Signed(publicKey, envelope)
    }

    fun verify(stop: LocalDraftStop, envelope: JSONObject, trustedPublicKey: String) {
        check(envelope.keys().asSequence().toSet() == setOf("schemaVersion", "algorithm", "keyId", "core", "signature"))
        check(envelope.get("schemaVersion") == 1 && envelope.getString("algorithm") == "ECDSA_P256_SHA256_DER")
        val canonical = SubmissionJson.canonical(core(stop))
        check(SubmissionJson.canonical(envelope.getJSONObject("core")) == canonical)
        val der = Base64.getDecoder().decode(trustedPublicKey)
        check(Base64.getEncoder().encodeToString(der) == trustedPublicKey)
        check(envelope.getString("keyId") == E2aWire.sha(der))
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(der))
        check(key is ECPublicKey && isP256(key))
        val encoded = envelope.getString("signature"); val signature = Base64.getDecoder().decode(encoded)
        check(Base64.getEncoder().encodeToString(signature) == encoded)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(key); verifier.update((DOMAIN + canonical).toByteArray(Charsets.UTF_8))
        check(verifier.verify(signature)) { "原机停止签名不可核验，保留原件" }
    }

    private fun isP256(key: ECPublicKey): Boolean {
        val parameters = AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }
        val expected = parameters.getParameterSpec(ECParameterSpec::class.java)
        val actual = key.params
        return actual.curve == expected.curve && actual.generator == expected.generator &&
            actual.order == expected.order && actual.cofactor == expected.cofactor
    }
}
