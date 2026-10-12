package com.acrylic.outbound.pda.rescue

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Instant
import java.util.Base64
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class RescueDelegationTest {
    private val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val authority get() = JSONObject().put("keyId", "synthetic-rescue-root").put("algorithm", "RS256")
        .put("publicKey", Base64.getEncoder().encodeToString(key.public.encoded))
    private val device = UUID.randomUUID().toString()
    private val order = UUID.randomUUID().toString()
    private val now = Instant.parse("2026-10-12T00:00:00Z")
    private fun claims(expires: String? = null): JSONObject = JSONObject().put("iss", "acrylic-outbound")
        .put("aud", RescueDelegation.AUDIENCE).put("purpose", "SUPERVISOR_READ_ONLY_RESCUE")
        .put("grantId", UUID.randomUUID().toString()).put("rescuerId", UUID.randomUUID().toString())
        .put("issuerId", UUID.randomUUID().toString()).put("deviceId", device).put("operatorId", UUID.randomUUID().toString())
        .put("warehouseId", UUID.randomUUID().toString()).put("deviceKeySha256", "a".repeat(64))
        .put("orderScope", JSONArray().put(JSONObject().put("source", "TEMP").put("localOrderId", order)))
        .put("issuedAt", now.toString()).put("expiresAt", expires ?: JSONObject.NULL)
        .put("capabilities", JSONObject().put("readOriginalAllowed", true).put("mayResumeScanning", false)
            .put("maySubmit", false).put("releaseAllowed", false).put("stockReleased", false).put("localDeletionAllowed", false))
    private fun token(claims: JSONObject): String {
        fun encode(text: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray(Charsets.UTF_8))
        val text = encode("""{"alg":"RS256","typ":"JWT","kid":"synthetic-rescue-root"}""") + "." + encode(claims.toString())
        val signature = Signature.getInstance("SHA256withRSA").apply { initSign(key.private); update(text.toByteArray(Charsets.US_ASCII)) }.sign()
        return text + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature)
    }
    private fun verify(value: JSONObject) = RescueDelegation.verify(token(value), authority, device to "a".repeat(64))
    @Test fun explicitlyNonExpiringOriginalOnlyScopeSurvivesUnavailableCurrentTimeWithoutNewWorkRights() {
        val original = claims(); val verified = verify(original); verified.validAt(null)
        assertEquals(setOf(RescueTarget("TEMP", order)), verified.targets)
        val grant = RescueReadGrant(verified.identity, targets = verified.targets) {}
        val selected = JSONObject().put("source", "TEMP").put("deviceId", verified.identity.deviceId).put("operatorId", verified.identity.operatorId)
            .put("warehouseId", verified.identity.warehouseId).put("localOrderId", order)
        grant.check(selected)
        assertThrows(IllegalStateException::class.java) { grant.check(selected.put("source", "FORMAL")) }
        selected.put("source", "TEMP")
        assertThrows(IllegalStateException::class.java) { grant.check(selected.put("localOrderId", UUID.randomUUID().toString())) }
    }
    @Test fun finiteScopeRejectsExpiryUnknownTimeOrClockBeforeIssue() {
        val verified = verify(claims(now.plusSeconds(3600).toString())); verified.validAt(now.plusSeconds(1))
        for (time in listOf(null, now.minusSeconds(1), now.plusSeconds(3600)))
            assertThrows(IllegalStateException::class.java) { verified.validAt(time) }
    }
    @Test fun emptyDuplicateScopeOrOpenCapabilitiesAreNeverRescueAuthority() {
        val row = JSONObject().put("source", "TEMP").put("localOrderId", order)
        for (value in listOf(claims().put("orderScope", JSONArray()), claims().put("orderScope", JSONArray().put(row).put(row)),
            claims().apply { getJSONObject("capabilities").put("maySubmit", true) }, claims().put("accessToken", "synthetic-forbidden")))
            assertThrows(IllegalStateException::class.java) { verify(value) }
    }
    @Test fun anotherDeviceKeyOrFileProvidedSigningAuthorityCannotReplaceTheTrustedRoot() {
        assertThrows(IllegalStateException::class.java) { RescueDelegation.verify(token(claims()), authority, device to "b".repeat(64)) }
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val replaced = authority.put("publicKey", Base64.getEncoder().encodeToString(other.public.encoded))
        assertThrows(IllegalStateException::class.java) { RescueDelegation.verify(token(claims()), replaced, device to "a".repeat(64)) }
    }
}
