package com.acrylic.outbound.pda.relay

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class RelayReceiptVerifierTest {
    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val device = ByteArray(32) { it.toByte() }
    private val key = JSONObject().put("algorithm", "RS256").put("keyId", "synthetic")
        .put("publicKey", Base64.getEncoder().encodeToString(pair.public.encoded))
    private val identity = JSONObject().apply {
        for (field in listOf("operatorId", "deviceId", "warehouseId", "localOrderId")) put(field, UUID.randomUUID().toString())
    }
    private val body = SubmissionJson.canonical(JSONObject().put("submissionId", UUID.randomUUID().toString()).put("sourceRecoveryEpoch", "1"))
    private fun original(): JSONObject = JSONObject(identity.toString()).apply {
        for (field in listOf("relayId", "resultId", "exportId", "relayOperatorId")) put(field, UUID.randomUUID().toString())
        put("purpose", "PDA_ORIGINAL_RELAY_RESULT_V1").put("archiveSha256", "a".repeat(64))
        put("deviceKeySha256", JwtVerifier.sha(device)).put("submissionId", JSONObject(body).getString("submissionId"))
        put("payloadSha256", JwtVerifier.sha(body.toByteArray())).put("sourceRecoveryEpoch", "1").put("localDeletionAllowed", false)
        put("businessReceipt", JSONObject().put("status", "REVIEW_REQUIRED"))
    }
    private fun signed(receipt: JSONObject): ByteArray {
        fun encoded(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val header = JSONObject().put("alg", "RS256").put("typ", "JWT").put("kid", "synthetic")
        val claims = JSONObject().put("iss", "acrylic-outbound").put("aud", "acrylic-pda-relay-result").put("iat", 1).put("receipt", receipt)
        val payload = encoded(header.toString().toByteArray()) + "." + encoded(claims.toString().toByteArray())
        val signature = Signature.getInstance("SHA256withRSA").run { initSign(pair.private); update(payload.toByteArray()); sign() }
        return JSONObject(receipt.toString()).put("signedRelayReceipt", payload + "." + encoded(signature)).toString().toByteArray()
    }
    @Test fun signedOriginalUsesProvisionedRootAndRetainsReviewBoundary() {
        assertEquals("REVIEW_REQUIRED", RelayReceiptVerifier.verify(signed(original()), identity, body, device, key).getString("status"))
    }
    @Test fun correctlySignedWrongSourceCannotChangeLocalResult() {
        for (field in listOf("operatorId", "deviceId", "warehouseId", "localOrderId", "submissionId", "payloadSha256", "sourceRecoveryEpoch", "deviceKeySha256")) {
            val file = original().put(field, if (field.endsWith("Sha256")) "b".repeat(64) else UUID.randomUUID().toString())
            assertThrows(Exception::class.java) { RelayReceiptVerifier.verify(signed(file), identity, body, device, key) }
        }
    }
    @Test fun wrongRootAndUnsignedFileChangesAreRejected() {
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val root = JSONObject(key.toString()).put("publicKey", Base64.getEncoder().encodeToString(other.public.encoded))
        assertThrows(Exception::class.java) { RelayReceiptVerifier.verify(signed(original()), identity, body, device, root) }
        val file = JSONObject(signed(original()).toString(Charsets.UTF_8)).put("localDeletionAllowed", true)
        assertThrows(Exception::class.java) { RelayReceiptVerifier.verify(file.toString().toByteArray(), identity, body, device, key) }
    }
    @Test fun oldOrDifferentTerminalResultsCannotRegressLocalFacts() {
        val review = JSONObject().put("status", "REVIEW_REQUIRED")
        assertThrows(Exception::class.java) { RelayReceiptVerifier.requireForward("SYNCED", "{}", "REVIEW_REQUIRED", review) }
        assertThrows(Exception::class.java) { RelayReceiptVerifier.requireForward("REVIEW_REQUIRED", review.toString(), "RECEIVING", review) }
        val success = JSONObject().put("status", "SUCCEEDED")
        RelayReceiptVerifier.requireForward("SYNCED", success.toString(), "SYNCED", success)
        assertThrows(Exception::class.java) { RelayReceiptVerifier.requireForward("SYNCED", success.toString(), "SYNCED", JSONObject().put("status", "changed")) }
    }
}
