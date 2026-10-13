package com.acrylic.outbound.pda.independent

import com.acrylic.outbound.pda.rescue.RescueSignedJson
import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

internal data class IndependentCaptureIdentity(val operatorId: String, val deviceId: String, val warehouseId: String,
    val deviceKeySha256: String, val serverRecoveryEpoch: String)

internal data class IndependentCredentialProof(val id: String, val identity: IndependentCaptureIdentity,
    val userVersion: String, val deviceVersion: String, val issued: Instant, val expires: Instant, val originalToken: String) {
    fun at(now: Instant) { check(now >= issued && now < expires) { "独立采集期限未生效或已到期；原件保留" } }
    companion object {
        const val AUDIENCE = "acrylic-pda-independent-v1"
        fun verify(token: String, trusted: JSONObject): IndependentCredentialProof {
            check(token.length in 1..16_384 && token.split('.').all { it.matches(Regex("[A-Za-z0-9_-]+")) })
            JwtVerifier.verify(token, trusted, AUDIENCE, 16_384)
            val parts = token.split('.'); val decoder = Base64.getUrlDecoder()
            val header = RescueSignedJson.read(decoder.decode(parts[0]))
            check(header.keys().asSequence().toSet() == setOf("alg", "typ", "kid"))
            val value = RescueSignedJson.read(decoder.decode(parts[1]))
            check(value.keys().asSequence().toSet() == setOf("iss", "aud", "purpose", "scope", "credentialId", "operatorId",
                "deviceId", "warehouseId", "deviceKeySha256", "serverRecoveryEpoch", "userAuthVersion", "deviceVersion",
                "issuedAt", "expiresAt", "capabilities"))
            check(value.getString("purpose") == "INDEPENDENT_CAPTURE" && value.getString("scope") == "pda:independent")
            capabilities(value.getJSONObject("capabilities"))
            val digest = value.getString("deviceKeySha256"); check(digest.matches(Regex("[0-9a-f]{64}")))
            val identity = IndependentCaptureIdentity(uuid(value, "operatorId"), uuid(value, "deviceId"), uuid(value, "warehouseId"),
                digest, positive(value, "serverRecoveryEpoch"))
            val issued = Instant.parse(value.getString("issuedAt")); val expires = Instant.parse(value.getString("expiresAt"))
            check(expires > issued && Duration.between(issued, expires) <= Duration.ofHours(48))
            return IndependentCredentialProof(uuid(value, "credentialId"), identity, positive(value, "userAuthVersion"),
                positive(value, "deviceVersion"), issued, expires, token)
        }
        private fun capabilities(value: JSONObject) {
            val closed = setOf("maySubmit", "mayConfirm", "stockAvailable", "localDeletionAllowed")
            check(value.keys().asSequence().toSet() == closed + "mayCollectIndependent")
            check(value.get("mayCollectIndependent") == true && closed.all { value.get(it) == false })
        }
        private fun uuid(value: JSONObject, field: String) = value.getString(field).also { check(UUID.fromString(it).toString() == it) }
        private fun positive(value: JSONObject, field: String) = value.getString(field).also {
            check(it.matches(Regex("[1-9][0-9]{0,18}")) && it.toLong() > 0)
        }
    }
}
