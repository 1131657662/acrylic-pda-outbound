package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.security.DeviceKeys
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

object E2aStopProof {
    data class Signed(val publicKey: String, val envelope: JSONObject)
    data class Facts(val warehouseId: String, val sourceId: String, val submissionId: String,
        val workRound: Long, val originalOperatorId: String, val freezeDeviceId: String,
        val sourceRecoveryEpoch: Long, val stopEventId: String, val captureHighWatermark: Long,
        val auditThroughSeq: Long, val stopTransactionVersion: Long, val manifestId: String,
        val manifestVersion: Long, val manifestBytesHash: String, val setHash: String)

    fun core(facts: Facts): JSONObject {
        listOf(facts.warehouseId, facts.sourceId, facts.submissionId, facts.originalOperatorId,
            facts.freezeDeviceId, facts.stopEventId, facts.manifestId).forEach(E2aItemValidation::uuid)
        check(listOf(facts.workRound, facts.auditThroughSeq, facts.stopTransactionVersion,
            facts.manifestVersion).all { it in 1..9007199254740991L })
        check(listOf(facts.sourceRecoveryEpoch, facts.captureHighWatermark)
            .all { it in 0..9007199254740991L })
        check(listOf(facts.manifestBytesHash, facts.setHash).all { it.matches(Regex("[0-9a-f]{64}")) })
        return JSONObject().put("warehouseId", facts.warehouseId).put("sourceId", facts.sourceId)
            .put("submissionId", facts.submissionId).put("workRound", facts.workRound)
            .put("originalOperatorId", facts.originalOperatorId).put("freezeDeviceId", facts.freezeDeviceId)
            .put("sourceRecoveryEpoch", facts.sourceRecoveryEpoch).put("stopEventId", facts.stopEventId)
            .put("captureHighWatermark", facts.captureHighWatermark).put("auditThroughSeq", facts.auditThroughSeq)
            .put("stopTransactionVersion", facts.stopTransactionVersion).put("manifestId", facts.manifestId)
            .put("manifestVersion", facts.manifestVersion).put("manifestBytesHash", facts.manifestBytesHash)
            .put("setHash", facts.setHash)
    }

    fun sign(facts: Facts, keys: DeviceKeys): JSONObject = sign(facts, keys.publicKey(false), keys::sign)

    fun sign(facts: Facts, publicKeyDerBase64: String, signer: (String) -> String): JSONObject {
        val canonical = SubmissionJson.canonical(core(facts))
        val input = "ACRYLIC-R06/E2A/STOP/1\n$canonical"
        val der = Base64.getDecoder().decode(publicKeyDerBase64)
        val envelope = JSONObject().put("schemaVersion", 1).put("algorithm", "ECDSA_P256_SHA256_DER")
            .put("keyId", E2aWire.sha(der)).put("core", JSONObject(canonical)).put("signature", signer(input))
        verify(facts, envelope, publicKeyDerBase64)
        return envelope
    }

    fun verify(facts: Facts, envelope: JSONObject, publicKeyDerBase64: String) {
        check(envelope.keys().asSequence().toSet() == setOf("schemaVersion", "algorithm", "keyId", "core", "signature"))
        check(envelope.get("schemaVersion") == 1 && envelope.getString("algorithm") == "ECDSA_P256_SHA256_DER")
        val canonical = SubmissionJson.canonical(core(facts))
        check(SubmissionJson.canonical(envelope.getJSONObject("core")) == canonical)
        val der = Base64.getDecoder().decode(publicKeyDerBase64)
        check(Base64.getEncoder().encodeToString(der) == publicKeyDerBase64)
        check(envelope.getString("keyId") == E2aWire.sha(der))
        val signatureBase64 = envelope.getString("signature")
        val signature = Base64.getDecoder().decode(signatureBase64)
        check(Base64.getEncoder().encodeToString(signature) == signatureBase64)
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(der))
        check(publicKey is ECPublicKey && isP256(publicKey))
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update("ACRYLIC-R06/E2A/STOP/1\n$canonical".toByteArray(Charsets.UTF_8))
        check(verifier.verify(signature)) { "设备 STOP 签名与原公钥不匹配" }
    }

    private fun isP256(key: ECPublicKey): Boolean {
        val parameters = AlgorithmParameters.getInstance("EC")
        parameters.init(ECGenParameterSpec("secp256r1"))
        val expected = parameters.getParameterSpec(ECParameterSpec::class.java)
        val actual = key.params
        return actual.curve == expected.curve && actual.generator == expected.generator &&
            actual.order == expected.order && actual.cofactor == expected.cofactor
    }
}
