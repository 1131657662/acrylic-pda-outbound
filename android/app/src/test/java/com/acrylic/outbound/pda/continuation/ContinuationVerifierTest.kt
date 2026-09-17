package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.recall.LocalRecall
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class ContinuationVerifierTest {
    private val fixture get() = JSONObject(File(checkNotNull(System.getProperty("pda.continuationFixture"))).readText())
    private fun local(f: JSONObject): LocalRecall {
        val body = f.getJSONObject("recovery"); val payload = SubmissionJson.canonical(body)
        return LocalRecall(body.getString("changeRequestId"), body.getString("assignmentId"), "{}", body.getString("recoveryPackageId"),
            payload, JwtVerifier.sha(payload.toByteArray(Charsets.UTF_8)), "RECALLED")
    }
    @Test fun realServerTicketPreservesRecoveryAndIssuesNewQualification() {
        val f = fixture; val a = ContinuationVerifier.verify(local(f), f.getJSONObject("activation"), f.getJSONObject("signingKey"))
        assertNotEquals(f.getJSONObject("recovery").getString("assignmentId"), a.getString("id"))
        assertEquals("2", a.getString("startOrderScanSeq"))
    }
    @Test fun changedTicketOrHighWatermarkCannotResumeScanning() {
        val f = fixture
        assertThrows(IllegalStateException::class.java) { ContinuationVerifier.verify(local(f), f.getJSONObject("activation").put("nextOrderScanSeq", "1"), f.getJSONObject("signingKey")) }
    }
    @Test fun alteredRetainedFactOrWrongRecoveryPackageIsRejected() {
        val f = fixture; val old = local(f); val body = JSONObject(old.payload!!)
        body.getJSONArray("scans").getJSONObject(0).put("savedAt", "2026-01-01T00:00:00Z")
        val payload = SubmissionJson.canonical(body)
        assertThrows(IllegalStateException::class.java) { ContinuationVerifier.verify(old.copy(payload = payload, sha256 = JwtVerifier.sha(payload.toByteArray())), f.getJSONObject("activation"), f.getJSONObject("signingKey")) }
    }
}
