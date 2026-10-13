package com.acrylic.outbound.pda.independent

import com.acrylic.outbound.pda.security.TimeSample
import com.acrylic.outbound.pda.security.TrustedTime
import org.json.JSONObject
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Instant
import java.util.Base64
import java.util.UUID

/** Synthetic server authority and durable bytes; real Android Keystore/HTTP get separate evidence. */
internal class IndependentCredentialFixture {
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val trusted get() = JSONObject().put("keyId", "synthetic-independent").put("algorithm", "RS256")
        .put("publicKey", Base64.getEncoder().encodeToString(keys.public.encoded))
    val issued = Instant.parse("2026-10-13T10:00:00Z")
    val device = id(); val operator = id(); val warehouse = id(); val login = id()
    var sample = TimeSample(1000, issued.toEpochMilli(), 5)
    var binding = device to "a".repeat(64)
    var session = actor()
    val storage = Storage()
    val store get() = IndependentCredentialStore(storage)
    val password get() = "synthetic-independent-local-secret".toCharArray()
    fun context() = IndependentCredentialContext(JSONObject(session.toString()), binding, sample)
    fun actor() = JSONObject().put("userId", operator).put("deviceId", device).put("warehouseId", warehouse)
        .put("serverRecoveryEpoch", "1").put("userAuthVersion", "1").put("deviceVersion", "1").put("localSessionId", login)
        .put("timeAnchor", TrustedTime.anchor(issued.toString(), sample)).put("signingKey", trusted)
        .put("accessToken", "synthetic-bearer-not-for-persistence").put("refreshToken", "synthetic-refresh-not-for-persistence")
    fun claims() = JSONObject().put("iss", "acrylic-outbound").put("aud", IndependentCredentialProof.AUDIENCE)
        .put("purpose", "INDEPENDENT_CAPTURE").put("scope", "pda:independent").put("credentialId", id())
        .put("operatorId", operator).put("deviceId", device).put("warehouseId", warehouse).put("deviceKeySha256", binding.second)
        .put("serverRecoveryEpoch", "1").put("userAuthVersion", "1").put("deviceVersion", "1")
        .put("issuedAt", issued.toString()).put("expiresAt", issued.plusSeconds(48*3600).toString())
        .put("capabilities", JSONObject().put("mayCollectIndependent", true).put("maySubmit", false).put("mayConfirm", false)
            .put("stockAvailable", false).put("localDeletionAllowed", false))
    fun token(claims: JSONObject) = rawToken(claims.toString())
    fun rawToken(payload: String): String {
        val text = encode("""{"alg":"RS256","typ":"JWT","kid":"synthetic-independent"}""") + "." + encode(payload)
        val signature = Signature.getInstance("SHA256withRSA").apply { initSign(keys.private); update(text.toByteArray(Charsets.US_ASCII)) }.sign()
        return text + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature)
    }
    fun receipt(value: JSONObject = claims()) = JSONObject().put("credentialId", value.getString("credentialId"))
        .put("signedCredential", token(value)).put("issuedAt", value.getString("issuedAt")).put("expiresAt", value.getString("expiresAt"))
    fun provision(): IndependentCredentialIntent {
        val request = store.prepare(context())
        store.receive(IndependentCredentialIncoming(request, receipt(), context()), password)
        return request
    }
    fun advance(millis: Long) { sample = sample.copy(elapsed = sample.elapsed + millis, wall = sample.wall + millis) }
    class Storage : IndependentCredentialStorage {
        var bytes: String? = null; var failWrite = false
        override fun read() = bytes?.let(::JSONObject)
        override fun write(state: JSONObject) { check(!failWrite) { "synthetic disk full" }; bytes = state.toString() }
    }
    private fun id() = UUID.randomUUID().toString()
    private fun encode(text: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray(Charsets.UTF_8))
}
