package com.acrylic.outbound.pda.recall

import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class RecallVerifierTest {
    private val fixture get() = JSONObject(File(checkNotNull(System.getProperty("pda.recallFixture"))).readText())
    private fun local(f: JSONObject): LocalRecall {
        val body = f.getJSONObject("body"); val payload = SubmissionJson.canonical(body)
        return LocalRecall(body.getString("changeRequestId"), body.getString("assignmentId"), "{}",
            body.getString("recoveryPackageId"), payload, JwtVerifier.sha(payload.toByteArray(Charsets.UTF_8)), "RECOVERY_FROZEN")
    }
    @Test fun realServerProofBindsTheOriginalRecoveryPackage() {
        val f = fixture; RecallVerifier.verify(local(f), f.getJSONObject("receipt"), f.getJSONObject("signingKey"))
    }
    @Test fun modifiedProofOrDifferentPackageCannotConfirmStop() {
        val f = fixture; val local = local(f)
        assertThrows(IllegalStateException::class.java) { RecallVerifier.verify(local.copy(sha256 = "0".repeat(64)), f.getJSONObject("receipt"), f.getJSONObject("signingKey")) }
        assertThrows(IllegalStateException::class.java) { RecallVerifier.verify(local, f.getJSONObject("receipt").put("stockReleased", true), f.getJSONObject("signingKey")) }
    }
}
