package com.acrylic.outbound.pda.temporary

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
class TemporarySubmissionContractTest {
    private fun fixture() = JSONObject(File(checkNotNull(System.getProperty("pda.temporarySubmissionFixture"))).readText())
    private fun local(body: JSONObject): TemporarySubmission {
        val payload = SubmissionJson.canonical(body)
        return TemporarySubmission(body.getString("submissionId"), body.getJSONObject("source").getString("clientOrderId"),
            payload, JwtVerifier.sha(payload.toByteArray(Charsets.UTF_8)))
    }
    @Test fun realServerProofsVerifyBothAbortBeforeBodyAndAfterReview() {
        val f = fixture(); val key = f.getJSONObject("signingKey"); val local = local(f.getJSONObject("body"))
        assertEquals("REVIEW_REQUIRED", TemporarySubmissionReceipt.verify(local, f.getJSONObject("review"), key))
        assertEquals("ABORTED", TemporarySubmissionReceipt.verify(local.copy(receipt = f.getJSONObject("review").toString()), f.getJSONObject("abort"), key))
        assertEquals("ABORTED", TemporarySubmissionReceipt.verify(local(f.getJSONObject("beforeBody")), f.getJSONObject("beforeAbort"), key))
        assertTrue(f.getJSONObject("beforeAbort").isNull("orderId")); assertTrue(f.getJSONObject("beforeAbort").isNull("payloadSha256"))
    }
    @Test fun changedSignedReasonOrOriginalSourceIsRejected() {
        val f = fixture(); val key = f.getJSONObject("signingKey"); val local = local(f.getJSONObject("body"))
        val altered = JSONObject(f.getJSONObject("abort").toString()).put("reason", "改写")
        assertTrue(runCatching { TemporarySubmissionReceipt.verify(local, altered, key) }.isFailure)
        assertTrue(runCatching { TemporarySubmissionReceipt.verify(local(f.getJSONObject("beforeBody")), f.getJSONObject("abort"), key) }.isFailure)
    }
    @Test fun realPreparationFreezesAndroidBodyForBackendValidation() = kotlinx.coroutines.runBlocking<Unit>(kotlinx.coroutines.Dispatchers.IO) {
        val root = java.nio.file.Files.createTempDirectory("temp-submit-contract").toFile()
        try {
            TemporaryWorkFixture(root, contract = fixture().getJSONObject("preparation")).use { f ->
                f.start("1"); f.barcodes.forEach { f.scan(it) }
                val frozen = TemporaryFreeze(f.store).freeze(f.id, "")
                val file = File(checkNotNull(System.getProperty("pda.temporarySubmissionFixture"))).resolveSibling("pda-temporary-android-body.json")
                file.writeText(frozen.payload)
                assertEquals("FROZEN", frozen.state)
            }
        } finally { root.deleteRecursively() }
    }
}
