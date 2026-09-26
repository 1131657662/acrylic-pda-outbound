package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class E2aIntakeReceiptContractTest {
    @Test fun allFourRealServerHttpReceiptsVerifyAgainstTheirExactOriginalRequests() {
        val vector = vector()
        assertEquals("SYNTHETIC_ISOLATED_MYSQL_REAL_HTTP", vector.getString("fixturePurpose"))
        val entries = vector.getJSONArray("entries")
        assertEquals(4, entries.length())
        for (index in 0 until entries.length()) {
            val entry = entries.getJSONObject(index)
            val response = bytes(entry, "responseBase64")
            val expected = expected(vector, entry)
            val verified = E2aReceiptVerifier.verify(expected, response, vector.getJSONObject("signingKey"))
            assertEquals(entry.getString("operation"), verified.getString("operationKind"))
            assertFalse(verified.getJSONObject("capabilities").getBoolean("releaseAllowed"))
        }
    }

    @Test fun realReceiptCannotBeReboundToAnotherOriginalKeyOrPublicKey() {
        val vector = vector(); val entry = vector.getJSONArray("entries").getJSONObject(0)
        val expected = expected(vector, entry); val response = bytes(entry, "responseBase64")
        assertThrows(IllegalStateException::class.java) {
            E2aReceiptVerifier.verify(expected.copy(request = expected.request.copy(originalKey = java.util.UUID.randomUUID().toString())),
                response, vector.getJSONObject("signingKey"))
        }
        assertThrows(IllegalStateException::class.java) { E2aReceiptVerifier.verify(expected, response, E2aTestReceipts().key) }
    }

    private fun expected(vector: JSONObject, entry: JSONObject): E2aReceiptVerifier.Expected {
        val entries = vector.getJSONArray("entries")
        val stopBody = JSONObject(bytes(entries.getJSONObject(2), "requestBase64").toString(Charsets.UTF_8))
        val verifyReceipt = JSONObject(bytes(entries.getJSONObject(3), "responseBase64").toString(Charsets.UTF_8))
        val core = stopBody.getJSONObject("stopProof").getJSONObject("core")
        val stop = LocalE2aStop(vector.getString("clientOrderId"), vector.getString("submissionId"),
            vector.getString("authorizationId"), vector.getString("warehouseId"), vector.getString("originalOperatorId"),
            vector.getString("freezeDeviceId"), vector.getLong("sourceRecoveryEpoch"), vector.getString("snapshotId"),
            vector.getString("manifestId"), vector.getString("openOriginalKey"), core.getString("stopEventId"),
            core.getLong("captureHighWatermark"), core.getLong("auditThroughSeq"), 2, 0)
        val plan = LocalE2aTransfer(stop.clientOrderId, SubmissionJson.canonical(vector.getJSONObject("manifestCore")),
            vector.getString("manifestBytesHash"), vector.getString("setHash"), vector.getString("stopOriginalKey"),
            verifyReceipt.getString("originalKey"), 1, 5, "unused-in-receipt-test", "unused-in-receipt-test")
        val response = JSONObject(bytes(entry, "responseBase64").toString(Charsets.UTF_8))
        val request = E2aTransferRequests.saved(stop.clientOrderId, entry.getString("operation"),
            if (entry.getString("operation") == "PAGE") 1 else 0, response.getString("originalKey"),
            JSONObject(bytes(entry, "requestBase64").toString(Charsets.UTF_8)))
        return E2aReceiptVerifier.Expected(stop, plan, request)
    }

    private fun vector() = JSONObject(File(checkNotNull(System.getProperty("pda.e2aIntakeVector"))).readText())
    private fun bytes(value: JSONObject, name: String) = Base64.getDecoder().decode(value.getString(name))
}
