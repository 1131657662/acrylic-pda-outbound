package com.acrylic.outbound.pda.retention

import com.acrylic.outbound.pda.submission.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.*
import java.util.Base64

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class SubmissionArchiveVerifierTest {
    private val fixture get() = JSONObject(File(checkNotNull(System.getProperty("pda.archiveFixture"))).readText())
    private val keys by lazy { KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair() }
    private val key get() = JSONObject().put("algorithm", "RS256").put("keyId", "test-archive")
        .put("publicKey", Base64.getEncoder().encodeToString(keys.public.encoded))
    private fun verify(f: JSONObject, response: JSONObject, signingKey: JSONObject = key) {
        val body = f.getJSONObject("body"); val proof = f.getJSONObject("confirmation")
        val local = LocalSubmission(body.getString("submissionId"), body.getString("assignmentId"), SubmissionJson.canonical(body),
            proof.getString("payloadSha256"), "SYNCED", f.getJSONObject("receipt").toString())
        val auth = JSONObject().put("userId", proof.getString("operatorId")).put("deviceId", proof.getString("freezeDeviceId"))
            .put("warehouseId", proof.getString("warehouseId")).put("serverRecoveryEpoch", proof.getString("recoveryEpoch"))
        SubmissionArchiveVerifier.verify(local, auth, SubmissionArchiveVerifier.request(local, f.getJSONObject("signingKey")), response, signingKey)
    }
    private fun sign(proof: JSONObject, audience: String = "acrylic-pda-archive", algorithm: String = "RS256"): JSONObject {
        val result = JSONObject(proof.toString()).apply { remove("signedArchive") }
        val header = JSONObject().put("alg", algorithm).put("typ", "JWT").put("kid", "test-archive")
        val claims = JSONObject().put("iss", "acrylic-outbound").put("aud", audience).put("archive", JSONObject(result.toString()))
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val raw = encoder.encodeToString(header.toString().toByteArray()) + "." + encoder.encodeToString(claims.toString().toByteArray())
        val signer = Signature.getInstance("SHA256withRSA").apply { initSign(keys.private); update(raw.toByteArray(Charsets.US_ASCII)) }
        return result.put("signedArchive", "$raw.${encoder.encodeToString(signer.sign())}")
    }
    @Test fun backendCanonicalHashesAndArchiveSignatureMatchAndroid() {
        val f = fixture; verify(f, f.getJSONObject("confirmation"), f.getJSONObject("signingKey"))
    }
    @Test fun validSignatureCannotAuthorizeAnotherIdentityOrEpoch() {
        val f = fixture
        for (field in listOf("submissionId", "assignmentId", "orderId", "warehouseId", "operatorId", "freezeDeviceId", "workRound", "sourceRecoveryEpoch", "recoveryEpoch")) {
            val changed = JSONObject(f.getJSONObject("confirmation").toString()).put(field, "other")
            assertThrows(IllegalStateException::class.java) { verify(f, sign(changed)) }
        }
    }
    @Test fun validSignatureCannotConfirmDifferentDigestsOrLengths() {
        val f = fixture
        for (field in listOf("payloadSha256", "receiptSha256", "payloadBytes", "receiptBytes", "completedAt")) {
            val changed = JSONObject(f.getJSONObject("confirmation").toString()).put(field, "0")
            assertThrows(IllegalStateException::class.java) { verify(f, sign(changed)) }
        }
    }
    @Test fun wrongSigningPurposeAlgorithmOrKeyIsRejected() {
        val f = fixture; val proof = f.getJSONObject("confirmation")
        assertThrows(IllegalStateException::class.java) { verify(f, sign(proof, audience = "acrylic-pda-receipt")) }
        assertThrows(IllegalStateException::class.java) { verify(f, sign(proof, algorithm = "none")) }
        assertThrows(IllegalStateException::class.java) { verify(f, sign(proof), f.getJSONObject("signingKey")) }
    }
    @Test fun oldArchiveDateAndShortRetentionCannotBeAccepted() {
        val f = fixture; val proof = f.getJSONObject("confirmation")
        assertThrows(IllegalStateException::class.java) { verify(f, sign(JSONObject(proof.toString()).put("archivedAt", "2000-01-01T00:00:00Z"))) }
        assertThrows(IllegalStateException::class.java) { verify(f, sign(JSONObject(proof.toString()).put("retainUntil", proof.getString("archivedAt")))) }
    }
    @Test fun signedDeletionPermissionCannotActivateCleanup() {
        val f = fixture
        assertThrows(IllegalStateException::class.java) { verify(f, sign(JSONObject(f.getJSONObject("confirmation").toString()).put("localDeletionAllowed", true))) }
    }
}
