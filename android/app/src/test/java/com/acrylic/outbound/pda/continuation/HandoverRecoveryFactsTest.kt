package com.acrylic.outbound.pda.continuation

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class HandoverRecoveryFactsTest {
    private val source = UUID.randomUUID().toString()
    private val order = UUID.randomUUID().toString()
    private val packageId = UUID.randomUUID().toString()
    private val savedId = UUID.randomUUID().toString()
    private val rejectedId = UUID.randomUUID().toString()

    @Test fun completeStoppedFactsAreAccepted() {
        HandoverRecoveryFacts.verify(stage(body()))
    }

    @Test fun approvedDispositionIsExcludedFromTotals() {
        val proof = JSONObject().put("captureId", savedId).put("action", "RELEASE")
            .put("approvedEvidenceId", UUID.randomUUID().toString())
        val body = body().put("dispositions", JSONArray().put(proof))
            .put("expectedCount", "0").put("expectedWeightGrams", "0")
        body.getJSONObject("captureSummary").put("saved", "0")
        HandoverRecoveryFacts.verify(stage(body))
    }

    @Test fun voidedScanIsExcludedFromTotals() {
        val body = body().put("expectedCount", "0").put("expectedWeightGrams", "0")
        body.getJSONArray("scans").getJSONObject(0).put("status", "VOID")
        body.getJSONObject("captureSummary").put("saved", "0").put("voided", "1")
        HandoverRecoveryFacts.verify(stage(body))
    }

    @Test fun inputGapIsRejected() {
        val body = body()
        body.getJSONArray("captures").getJSONObject(1).put("orderScanSeq", "3")
        assertThrows(IllegalStateException::class.java) { HandoverRecoveryFacts.verify(stage(body)) }
    }

    @Test fun committedInputWithoutScanIsRejected() {
        val body = body().put("scans", JSONArray())
        assertThrows(IllegalStateException::class.java) { HandoverRecoveryFacts.verify(stage(body)) }
    }

    @Test fun scanWithDifferentInputSequenceIsRejected() {
        val body = body()
        body.getJSONArray("scans").getJSONObject(0).put("orderScanSeq", "2")
        assertThrows(IllegalStateException::class.java) { HandoverRecoveryFacts.verify(stage(body)) }
    }

    @Test fun savedWeightMismatchIsRejected() {
        val body = body().put("expectedWeightGrams", "999")
        assertThrows(IllegalStateException::class.java) { HandoverRecoveryFacts.verify(stage(body)) }
    }

    @Test fun missingStopAuditIsRejected() {
        val body = body().put("events", JSONArray())
        assertThrows(Exception::class.java) { HandoverRecoveryFacts.verify(stage(body)) }
    }

    @Test fun dispositionWithoutMatchingActiveScanIsRejected() {
        val proof = JSONObject().put("captureId", rejectedId).put("action", "RELEASE")
        val body = body().put("dispositions", JSONArray().put(proof))
        assertThrows(IllegalStateException::class.java) { HandoverRecoveryFacts.verify(stage(body)) }
    }

    private fun body(): JSONObject {
        val captures = JSONArray().put(capture(savedId, 1, "COMMITTED"))
            .put(capture(rejectedId, 2, "INVALID"))
        val scans = JSONArray().put(JSONObject().put("captureId", savedId)
            .put("orderScanSeq", "1").put("status", "ACTIVE").put("weightGrams", "1250"))
        val stop = JSONObject().put("id", UUID.randomUUID().toString()).put("assignmentId", source)
            .put("captureId", JSONObject.NULL).put("type", "RECALL_STOPPED").put("detail", packageId)
        val summary = JSONObject().put("received", "2").put("saved", "1")
            .put("voided", "0").put("pending", "0").put("invalid", "1")
            .put("businessRejected", "0").put("discarded", "0")
        return JSONObject().put("schemaVersion", 2).put("recoveryPackageId", packageId)
            .put("assignmentId", source).put("orderId", order).put("captureHighWatermark", "2")
            .put("expectedCount", "1").put("expectedWeightGrams", "1250")
            .put("captures", captures).put("scans", scans).put("events", JSONArray().put(stop))
            .put("dispositions", JSONArray()).put("captureSummary", summary)
    }

    private fun capture(id: String, sequence: Int, state: String): JSONObject =
        JSONObject().put("id", id).put("orderScanSeq", sequence.toString()).put("state", state)

    private fun stage(body: JSONObject): LocalHandoverStage = LocalHandoverStage(
        UUID.randomUUID().toString(), source, order, UUID.randomUUID().toString(),
        UUID.randomUUID().toString(), UUID.randomUUID().toString(), 2, "", "", "",
        body.toString().toByteArray(Charsets.UTF_8))
}
