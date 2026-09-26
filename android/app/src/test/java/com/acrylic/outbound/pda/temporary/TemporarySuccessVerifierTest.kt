package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class TemporarySuccessVerifierTest {
    @Test fun realServerSignedSuccessKeepsZeroProductAndAcceptsLostPendingReceipt() {
        for (name in listOf("normal", "zeroProduct")) {
            val v = TemporarySuccessFixture.vector(name); val local = TemporarySuccessFixture.local(v)
            val receipt = verify(v, local)
            assertEquals("SUCCEEDED", receipt.getString("status")); assertFalse(receipt.getBoolean("mayResumeScanning"))
            assertEquals(receipt.toString(), verify(v, local.copy(receipt = null)).toString())
            if (name == "zeroProduct") assertEquals("0", receipt.getJSONArray("items").getJSONObject(1).getString("actualCount"))
        }
    }

    @Test fun individuallySignedSemanticMismatchesFailClosed() {
        for (name in listOf("normal", "zeroProduct")) {
            val v = TemporarySuccessFixture.vector(name); val negatives = v.getJSONObject("negativeReceipts")
            negatives.keys().forEach { field ->
                val envelope = JSONObject(v.getString("responseUtf8")).put("data", negatives.getJSONObject(field))
                assertTrue("$name/$field", runCatching { verify(v, raw = envelope.toString().toByteArray()) }.isFailure)
            }
        }
    }

    @Test fun wrongKeyUnsignedChangesAndNoncanonicalTokenFailClosed() {
        val v = TemporarySuccessFixture.vector(); val key = JSONObject(v.getJSONObject("signingKey").toString()).put("keyId", "unknown")
        assertTrue(runCatching { verify(v, key = key) }.isFailure)
        val envelope = JSONObject(v.getString("responseUtf8")); val data = envelope.getJSONObject("data")
        data.put("inventoryDeducted", false)
        assertTrue(runCatching { verify(v, raw = envelope.toString().toByteArray()) }.isFailure)
        data.put("inventoryDeducted", true).put("signedReceipt", data.getString("signedReceipt") + "=")
        assertTrue(runCatching { verify(v, raw = envelope.toString().toByteArray()) }.isFailure)
    }

    @Test fun duplicateTrailingMalformedAndCoercedJsonAreRejected() {
        val v = TemporarySuccessFixture.vector(); val original = v.getString("responseUtf8")
        val bad = listOf(original + "{}", "{\"code\":\"FAILED\"," + original.substring(1),
            original.replace("\"SUCCEEDED\"", "'SUCCEEDED'"), original.replace("\"receiptVersion\":1", "\"receiptVersion\":1.0"))
        bad.forEach { assertTrue(runCatching { verify(v, raw = it.toByteArray()) }.isFailure) }
        assertTrue(runCatching { verify(v, raw = byteArrayOf(0xC3.toByte(), 0x28)) }.isFailure)
    }

    @Test fun changedLocalAuthorizationOrSourceCannotBorrowOriginalSuccess() {
        val v = TemporarySuccessFixture.vector(); val local = TemporarySuccessFixture.local(v)
        for (field in listOf("authorizationId", "freezeDeviceId", "sourceRecoveryEpoch")) {
            val body = JSONObject(local.payload).put(field, "different-warehouse-or-source")
            val payload = SubmissionJson.canonical(body)
            assertTrue(runCatching { verify(v, local.copy(payload = payload,
                sha256 = JwtVerifier.sha(payload.toByteArray()))) }.isFailure)
        }
        assertTrue(runCatching { verify(v, local.copy(payload = local.payload + " ")) }.isFailure)
        assertTrue(runCatching { verify(v, local.copy(receipt = "{\"status\":\"ABORTED\"}")) }.isFailure)
    }

    private fun verify(v: JSONObject, local: TemporarySubmission = TemporarySuccessFixture.local(v),
        raw: ByteArray = v.getString("responseUtf8").toByteArray(), key: JSONObject = v.getJSONObject("signingKey")) =
        TemporarySuccessVerifier.verify(local, raw, key)
}
