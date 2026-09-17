package com.acrylic.outbound.pda.submission

import com.acrylic.outbound.pda.work.*
import org.json.JSONObject

object SubmissionRecords {
    fun capture(c: WorkCapture): JSONObject {
        val x = c.context
        return JSONObject().put("id", c.id).put("assignmentId", x.assignmentId).put("orderId", x.orderId)
            .put("originDeviceId", x.originDeviceId).put("operatorId", x.operatorId).put("warehouseId", x.warehouseId)
            .put("orderScanSeq", x.orderScanSeq.toString()).put("deviceCaptureSeq", x.deviceCaptureSeq.toString())
            .put("rawInput", x.rawInput).put("barcode", x.barcode).put("inputSource", x.inputSource).put("confirmationPolicy", x.confirmationPolicy)
            .put("receivedWallMs", x.receivedWallMs.toString()).put("receivedElapsedMs", x.receivedElapsedMs.toString()).put("receivedBoot", x.receivedBoot)
            .put("snapshotId", x.snapshotId).put("expectedDispatchCycle", x.expectedDispatchCycle?.toString() ?: JSONObject.NULL)
            .put("sourceRecoveryEpoch", x.sourceRecoveryEpoch).put("authorizationId", x.authorizationId).put("assignmentEpoch", x.assignmentEpoch)
            .put("orderRevision", x.orderRevision).put("inputRevision", x.inputRevision).put("state", c.state).put("error", c.error)
            .put("confirmedAt", c.confirmedAt ?: JSONObject.NULL).put("failureAckAt", c.failureAckAt ?: JSONObject.NULL)
    }
    fun scan(s: WorkScan): JSONObject = JSONObject().put("captureId", s.captureId).put("orderScanSeq", s.orderScanSeq.toString())
        .put("barcode", s.barcode).put("baleId", s.baleId).put("deliveryCode", s.deliveryCode).put("model", s.model).put("name", s.name)
        .put("rawWeight", s.rawWeight).put("weightGrams", s.weightGrams.toString()).put("expectedDispatchCycle", s.expectedDispatchCycle.toString())
        .put("savedAt", s.savedAt).put("localSeq", s.localSeq.toString()).put("status", s.status)
    fun event(e: WorkEvent): JSONObject = JSONObject().put("id", e.id).put("assignmentId", e.assignmentId)
        .put("captureId", e.captureId ?: JSONObject.NULL).put("originDeviceId", e.originDeviceId).put("operatorId", e.operatorId)
        .put("localSeq", e.localSeq.toString()).put("type", e.type).put("detail", e.detail)
        .put("wallMs", e.wallMs.toString()).put("elapsedMs", e.elapsedMs.toString()).put("boot", e.boot)
}
