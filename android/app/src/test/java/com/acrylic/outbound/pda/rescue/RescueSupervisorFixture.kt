package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.security.TimeSample
import com.acrylic.outbound.pda.security.TrustedTime
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Instant
import java.util.Base64
import java.util.UUID

/** Synthetic signing authority and in-memory durable bytes; never a real identity or Android key. */
internal class RescueSupervisorFixture {
    val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val trusted get() = JSONObject().put("keyId", "synthetic-rescue-root").put("algorithm", "RS256")
        .put("publicKey", Base64.getEncoder().encodeToString(key.public.encoded))
    val device = id(); val operator = id(); val warehouse = id(); val rescuer = id(); val grantId = id(); val order = id()
    val now = Instant.parse("2026-10-12T00:00:00Z")
    var sample = TimeSample(1000, now.toEpochMilli(), 1)
    var binding = device to "a".repeat(64)
    val password get() = "synthetic-local-rescue-password".toCharArray()
    val storage = Storage()
    class Storage : RescueCredentialStorage {
        var bytes: String? = null; var failWrite = false
        override fun read(): JSONObject? = bytes?.let(::JSONObject)
        override fun write(state: JSONObject) { check(!failWrite) { "synthetic full disk" }; bytes = state.toString() }
    }
    fun store() = RescueSupervisorStore(storage, { sample }, { binding })
    fun actor(): JSONObject = JSONObject().put("userId", rescuer).put("deviceId", device).put("warehouseId", warehouse)
        .put("timeAnchor", TrustedTime.anchor(now.toString(), sample)).put("accessToken", "synthetic-must-not-persist")
    fun claims(expires: Instant? = null): JSONObject = JSONObject().put("iss", "acrylic-outbound")
        .put("aud", RescueDelegation.AUDIENCE).put("purpose", "SUPERVISOR_READ_ONLY_RESCUE")
        .put("grantId", grantId).put("rescuerId", rescuer).put("issuerId", id()).put("deviceId", device)
        .put("operatorId", operator).put("warehouseId", warehouse).put("deviceKeySha256", "a".repeat(64))
        .put("orderScope", JSONArray().put(JSONObject().put("source", "TEMP").put("localOrderId", order)))
        .put("issuedAt", now.toString()).put("expiresAt", expires?.toString() ?: JSONObject.NULL)
        .put("capabilities", JSONObject().put("readOriginalAllowed", true).put("mayResumeScanning", false)
            .put("maySubmit", false).put("releaseAllowed", false).put("stockReleased", false).put("localDeletionAllowed", false))
    fun token(claims: JSONObject): String = rawToken(claims.toString())
    fun rawToken(claims: String): String {
        fun encode(text: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray(Charsets.UTF_8))
        val text = encode("""{"alg":"RS256","typ":"JWT","kid":"synthetic-rescue-root"}""") + "." + encode(claims)
        val signature = Signature.getInstance("SHA256withRSA").apply { initSign(key.private); update(text.toByteArray(Charsets.US_ASCII)) }.sign()
        return text + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature)
    }
    fun revocation(authorization: String): JSONObject = JSONObject().put("iss", "acrylic-outbound")
        .put("aud", RescueDelegation.AUDIENCE).put("purpose", "SUPERVISOR_READ_ONLY_RESCUE_REVOCATION")
        .put("rescuerId", rescuer).put("deviceId", device).put("operatorId", operator).put("warehouseId", warehouse)
        .put("deviceKeySha256", "a".repeat(64)).put("grantId", grantId).put("issuerId", id()).put("revokedAt", now.plusSeconds(1).toString())
        .put("authorizationSha256", RescueBundle.sha(authorization.toByteArray(Charsets.UTF_8)))
    fun provision(token: String, actor: JSONObject = actor()): RescueSupervisorStore.Provision =
        RescueSupervisorStore.Provision(token, trusted, actor)
    fun selected(): JSONObject = JSONObject().put("source", "TEMP").put("localOrderId", order)
        .put("deviceId", device).put("operatorId", operator).put("warehouseId", warehouse)
    private fun id() = UUID.randomUUID().toString()
}
