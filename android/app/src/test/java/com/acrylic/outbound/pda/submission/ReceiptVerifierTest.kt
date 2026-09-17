package com.acrylic.outbound.pda.submission

import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class ReceiptVerifierTest {
    private fun fixture() = JSONObject(File(checkNotNull(System.getProperty("pda.submissionFixture"))).readText())
    private fun local(f: JSONObject): LocalSubmission {
        val body = f.getJSONObject("body"); val receipt = f.getJSONObject("receipt")
        return LocalSubmission(body.getString("submissionId"), body.getString("assignmentId"), SubmissionJson.canonical(body), receipt.getString("payloadSha256"))
    }
    @Test fun serverSignedReceiptAndCanonicalBodyMatchAndroidExactly() {
        val f = fixture(); val local = local(f)
        assertEquals(local.sha256, com.acrylic.outbound.pda.security.JwtVerifier.sha(local.payload.toByteArray(Charsets.UTF_8)))
        assertEquals("SYNCED", ReceiptVerifier.verify(local, f.getJSONObject("receipt"), f.getJSONObject("signingKey")))
    }
    @Test fun tamperedReceiptCannotMarkLocalSubmissionSynced() {
        val f = fixture(); val local = local(f); val response = f.getJSONObject("receipt").put("actualWeightGrams", "1")
        assertThrows(IllegalStateException::class.java) { ReceiptVerifier.verify(local, response, f.getJSONObject("signingKey")) }
    }
    @Test fun validSignatureCannotAuthorizeDifferentFrozenBody() {
        val f = fixture(); val local = local(f).copy(sha256 = "0".repeat(64))
        assertThrows(IllegalStateException::class.java) { ReceiptVerifier.verify(local, f.getJSONObject("receipt"), f.getJSONObject("signingKey")) }
    }
}
