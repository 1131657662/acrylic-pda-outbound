package com.acrylic.outbound.pda.retention

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.acrylic.outbound.pda.submission.SubmissionJson
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class AuditArchiveVerifierTest {
    private val fixture get() = JSONObject(File(checkNotNull(System.getProperty("pda.auditArchiveFixture"))).readText())

    @Test fun realServerSignatureAndCanonicalPayloadMatchAndroid() {
        val f = fixture; val proof = f.getJSONObject("first"); val payload = f.getJSONObject("payload")
        val row = AuditArchive(proof.getString("recoveryEpoch"), proof.getString("originDeviceId"), 0, 3,
            proof.getString("payloadSha256"), "{}")
        AuditArchiveVerifier.verify(row, proof, f.getJSONObject("signingKey"), proof.getString("warehouseId"))
        assertEquals(proof.getString("payloadSha256"), AuditArchiveVerifier.digest(payload))
        assertEquals(proof.getString("byteCount"), SubmissionJson.canonical(payload).toByteArray(Charsets.UTF_8).size.toString())
    }

    @Test fun signedIdentityRangeDigestAndRetentionMismatchesAreRejected() {
        val proof = fixture.getJSONObject("first"); val factory = AuditProofFactory(); val row = factory.row(proof)
        val bad = mapOf("originDeviceId" to "other", "warehouseId" to "other", "recoveryEpoch" to "2",
            "afterSeq" to "1", "throughSeq" to "4", "payloadSha256" to "0".repeat(64), "byteCount" to "0",
            "retainUntil" to proof.getString("archivedAt"), "storagePolicy" to "UNKNOWN", "localDeletionAllowed" to true)
        bad.forEach { (field, value) ->
            assertThrows(IllegalStateException::class.java) {
                AuditArchiveVerifier.verify(row, factory.sign(JSONObject(proof.toString()).put(field, value)), factory.key, proof.getString("warehouseId"))
            }
        }
    }

    @Test fun alteredUnsignedResponseWrongPurposeAndWrongKeyCannotVerify() {
        val f = fixture; val proof = f.getJSONObject("first"); val factory = AuditProofFactory(); val row = factory.row(proof)
        val warehouse = proof.getString("warehouseId")
        assertThrows(IllegalStateException::class.java) {
            AuditArchiveVerifier.verify(row, JSONObject(proof.toString()).put("byteCount", "1"), f.getJSONObject("signingKey"), warehouse)
        }
        for (p in listOf(factory.sign(proof, audience = "acrylic-pda-archive"), factory.sign(proof, algorithm = "none")))
            assertThrows(IllegalStateException::class.java) { AuditArchiveVerifier.verify(row, p, factory.key, warehouse) }
        assertThrows(IllegalStateException::class.java) { AuditArchiveVerifier.verify(row, proof, factory.key, warehouse) }
    }

    private fun event(device: String, seq: Long) = JSONObject()
        .put("originDeviceId", device).put("localSeq", seq.toString())

    @Test fun payloadRequiresExactContinuousRange() {
        val events = JSONArray().put(event("device", 1)).put(event("device", 2))
        val body = AuditArchiveVerifier.payload("device", 0, 2, events)
        assertEquals("device", body.getString("originDeviceId"))
        assertEquals("2", body.getString("throughSeq"))
        assertThrows(IllegalStateException::class.java) {
            AuditArchiveVerifier.payload("device", 0, 3, events)
        }
        assertThrows(IllegalStateException::class.java) {
            AuditArchiveVerifier.payload("device", 0, 2, JSONArray().put(event("device", 2)).put(event("device", 3)))
        }
    }

    @Test fun payloadRejectsForeignDeviceAndOversizedSegment() {
        assertThrows(IllegalStateException::class.java) {
            AuditArchiveVerifier.payload("device", 0, 1, JSONArray().put(event("other", 1)))
        }
        val events = JSONArray()
        for (seq in 1..201) events.put(event("device", seq.toLong()))
        assertThrows(IllegalStateException::class.java) {
            AuditArchiveVerifier.payload("device", 0, 201, events)
        }
    }

    @Test fun prefixCannotHidePendingRowBeforeCompletedProof() {
        val pending = AuditArchive("1", "device", 0, 2, "a".repeat(64), "{}")
        val completed = pending.copy(afterSeq = 2, throughSeq = 3, proofJson = "{}", signingKeyJson = "{}")
        assertThrows(IllegalStateException::class.java) {
            AuditArchiveVerifier.prefix(listOf(pending, completed), "warehouse")
        }
    }

    @Test fun prefixRejectsMoreThanResourceLimit() {
        val rows = (0..1024).map { after ->
            AuditArchive("1", "device", after.toLong(), after + 1L, "b".repeat(64), "{}")
        }
        assertThrows(IllegalStateException::class.java) {
            AuditArchiveVerifier.prefix(rows, "warehouse")
        }
    }
}
