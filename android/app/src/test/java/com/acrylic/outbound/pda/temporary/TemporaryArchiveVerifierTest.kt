package com.acrylic.outbound.pda.temporary

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class TemporaryArchiveVerifierTest {
    @Test fun realServerArchiveProvesFiveYearsWithoutDeletionPermission() {
        val f = fixture(); val proof = verify(f)
        assertEquals(2L, proof.get("archiveVersion")); assertFalse(proof.getBoolean("localDeletionAllowed"))
        assertTrue(proof.isNull("assignmentId")); assertEquals("TEMP", proof.getJSONObject("source").getString("kind"))
    }
    @Test fun individuallyResignedSourceHashesSizesEpochAndRetentionMismatchesAreRejected() {
        val f = fixture(); val negatives = f.getJSONObject("negatives")
        negatives.keys().forEach { name -> assertTrue(name, runCatching { verify(f, negatives.getString(name).toByteArray()) }.isFailure) }
    }
    @Test fun unknownKeyTrailingAndDuplicateJsonCannotBecomeArchiveEvidence() {
        val f = fixture(); val raw = f.getString("responseUtf8")
        for (text in listOf(raw + "{}", "{\"code\":\"ERROR\"," + raw.substring(1)))
            assertTrue(runCatching { verify(f, text.toByteArray()) }.isFailure)
        assertTrue(runCatching { verify(f, key = JSONObject(f.getJSONObject("key").toString()).put("keyId", "unknown")) }.isFailure)
    }
    @Test fun alteredUnsignedArchiveCannotHideBehindOriginalSignature() {
        val f = fixture(); val response = JSONObject(f.getString("responseUtf8"))
        response.getJSONObject("data").put("localDeletionAllowed", true)
        assertTrue(runCatching { verify(f, response.toString().toByteArray()) }.isFailure)
    }
    private fun fixture() = JSONObject(File(checkNotNull(System.getProperty("pda.temporaryArchiveFixture"))).readText())
    private fun verify(f: JSONObject, raw: ByteArray = f.getString("responseUtf8").toByteArray(), key: JSONObject = f.getJSONObject("key")) =
        TemporaryArchiveVerifier.verify(TemporaryArchiveRequest(f.getString("submissionId"), f.getString("recoveryEpoch"),
            f.getString("requestJson"), f.getString("contextJson")), raw, key)
}
