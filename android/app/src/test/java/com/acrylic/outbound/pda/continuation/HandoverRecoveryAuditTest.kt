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
class HandoverRecoveryAuditTest {
    private val source = UUID.randomUUID().toString()
    private val device = UUID.randomUUID().toString()
    private val operator = UUID.randomUUID().toString()
    private val order = UUID.randomUUID().toString()
    private val warehouse = UUID.randomUUID().toString()
    private val savedId = UUID.randomUUID().toString()
    private val failedId = UUID.randomUUID().toString()

    @Test fun completeSourceAuditIsAccepted() {
        HandoverRecoveryAudit.verify(stage(body()))
    }

    @Test fun voidedScanWithSourceAuditIsAccepted() {
        val body = body()
        body.getJSONArray("scans").getJSONObject(0).put("status", "VOID")
        body.getJSONArray("events").put(6, event(savedId, 7, "VOID"))
            .put(event(null, 8, "RECALL_STOPPED"))
        HandoverRecoveryAudit.verify(stage(body))
    }

    @Test fun voidedScanWithoutSourceAuditIsRejected() {
        val body = body()
        body.getJSONArray("scans").getJSONObject(0).put("status", "VOID")
        assertThrows(IllegalStateException::class.java) { HandoverRecoveryAudit.verify(stage(body)) }
    }

    @Test fun reusedDeviceCaptureSequenceIsRejected() {
        val body = body()
        body.getJSONArray("captures").getJSONObject(1).put("deviceCaptureSeq", "1")
        assertThrows(IllegalStateException::class.java) { HandoverRecoveryAudit.verify(stage(body)) }
    }

    @Test fun differentOriginDeviceIsRejected() {
        val body = body()
        body.getJSONArray("captures").getJSONObject(0).put("originDeviceId", UUID.randomUUID().toString())
        assertThrows(IllegalStateException::class.java) { HandoverRecoveryAudit.verify(stage(body)) }
    }

    @Test fun missingFailureAcknowledgementIsRejected() {
        val body = body()
        body.getJSONArray("events").remove(5)
        assertThrows(IllegalStateException::class.java) { HandoverRecoveryAudit.verify(stage(body)) }
    }

    @Test fun scanPointingAtOtherAuditIsRejected() {
        val body = body()
        body.getJSONArray("scans").getJSONObject(0).put("localSeq", "4")
        assertThrows(IllegalStateException::class.java) { HandoverRecoveryAudit.verify(stage(body)) }
    }

    @Test fun repeatedEventSequenceIsRejected() {
        val body = body()
        body.getJSONArray("events").getJSONObject(3).put("localSeq", "3")
        assertThrows(IllegalStateException::class.java) { HandoverRecoveryAudit.verify(stage(body)) }
    }

    @Test fun auditUsingOtherOriginAssignmentIsRejected() {
        val body = body()
        body.getJSONArray("events").getJSONObject(2).put("assignmentId", UUID.randomUUID().toString())
        assertThrows(IllegalStateException::class.java) { HandoverRecoveryAudit.verify(stage(body)) }
    }

    private fun body(): JSONObject {
        val captures = JSONArray().put(capture(savedId, 1, "COMMITTED"))
            .put(capture(failedId, 2, "INVALID"))
        val events = JSONArray().put(event(savedId, 1, "RECEIVED"))
            .put(event(savedId, 2, "CONFIRM_REQUESTED"))
            .put(event(savedId, 3, "SAVED"))
            .put(event(failedId, 4, "RECEIVED"))
            .put(event(failedId, 5, "INVALID"))
            .put(event(failedId, 6, "FAILURE_ACKNOWLEDGED"))
            .put(event(null, 8, "RECALL_STOPPED"))
        val scan = JSONObject().put("captureId", savedId).put("localSeq", "3").put("status", "ACTIVE")
        return JSONObject().put("captures", captures).put("events", events).put("scans", JSONArray().put(scan))
    }

    private fun capture(id: String, sequence: Int, state: String) = JSONObject()
        .put("id", id).put("assignmentId", source).put("orderId", order)
        .put("originDeviceId", device).put("operatorId", operator).put("warehouseId", warehouse)
        .put("deviceCaptureSeq", sequence.toString()).put("state", state)

    private fun event(id: String?, sequence: Int, type: String) = JSONObject()
        .put("id", UUID.randomUUID().toString()).put("assignmentId", source)
        .put("originDeviceId", device).put("operatorId", operator)
        .put("captureId", id ?: JSONObject.NULL).put("localSeq", sequence.toString()).put("type", type)

    private fun stage(body: JSONObject): LocalHandoverStage {
        val sourceReceipt = JSONObject().put("assignmentId", source).put("deviceId", device).put("operatorId", operator)
        val manifest = JSONObject().put("stopReceipt", JSONObject().put("source", sourceReceipt))
        return LocalHandoverStage(UUID.randomUUID().toString(), source, order, UUID.randomUUID().toString(),
            UUID.randomUUID().toString(), warehouse, 2, "", manifest.toString(), "",
            body.toString().toByteArray(Charsets.UTF_8))
    }
}
