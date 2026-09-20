package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.retention.*
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class TemporaryAuditContractTest {
    @Test fun realServerMixedArchiveVerifiesWithOriginalTemporarySerialization() {
        val fixture = JSONObject(File(checkNotNull(System.getProperty("pda.temporaryAuditFixture"))).readText())
        val body = fixture.getJSONObject("payload"); val proof = fixture.getJSONObject("proof")
        val rows = body.getJSONArray("events"); val e = rows.getJSONObject(7); val source = e.getJSONObject("source")
        val event = TemporaryEvent(e.getString("id"), source.getString("clientOrderId"), null,
            e.getString("originDeviceId"), e.getString("operatorId"), e.getString("localSeq").toLong(),
            e.getString("type"), e.getString("detail"), e.getString("wallMs").toLong(), e.getString("elapsedMs").toLong(), e.getInt("boot"))
        rows.put(7, TemporaryAuditRecord(event, source.getString("creatorDeviceId"), e.getString("authorizationId"), event.operatorId).json())
        val payload = AuditArchiveVerifier.payload(body.getString("originDeviceId"), 0, 8, rows)
        val row = AuditArchive("1", body.getString("originDeviceId"), 0, 8, AuditArchiveVerifier.digest(payload), "{}")
        AuditArchiveVerifier.verify(row, proof, fixture.getJSONObject("signingKey"), proof.getString("warehouseId"))
        assertEquals(SubmissionJson.canonical(payload).toByteArray(Charsets.UTF_8).size.toString(), proof.getString("byteCount"))
        assertFalse(proof.getBoolean("localDeletionAllowed"))
        rows.getJSONObject(7).put("authorizationId", "00000000-0000-4000-8000-000000000000")
        assertThrows(IllegalStateException::class.java) {
            AuditArchiveVerifier.verify(row.copy(payloadSha256 = AuditArchiveVerifier.digest(payload)), proof,
                fixture.getJSONObject("signingKey"), proof.getString("warehouseId"))
        }
    }
}
