package com.acrylic.outbound.pda.temporary

import org.json.JSONObject

object TemporarySubmissionRecords {
    fun capture(c: TemporaryCapture): JSONObject = JSONObject().put("id", c.id).put("clientOrderId", c.clientOrderId)
        .put("originDeviceId", c.originDeviceId).put("operatorId", c.operatorId).put("orderScanSeq", c.orderScanSeq.toString())
        .put("deviceCaptureSeq", c.deviceCaptureSeq.toString()).put("rawInput", c.rawInput).put("barcode", c.barcode)
        .put("inputSource", c.inputSource).put("confirmationPolicy", if (c.inputSource == "BROADCAST") "AUTO_AFTER_VALIDATION" else "MANUAL")
        .put("receivedWallMs", c.receivedWallMs.toString()).put("receivedElapsedMs", c.receivedElapsedMs.toString()).put("receivedBoot", c.receivedBoot)
        .put("authorizationId", c.authorizationId).put("authorizationEpoch", c.authorizationEpoch).put("snapshotId", c.snapshotId)
        .put("sourceRecoveryEpoch", c.sourceRecoveryEpoch).put("orderRevision", c.orderRevision.toString())
        .put("expectedDispatchCycle", c.expectedDispatchCycle?.toString() ?: JSONObject.NULL).put("state", c.state).put("error", c.error)
        .put("confirmedAt", c.confirmedAt ?: JSONObject.NULL).put("failureAckAt", c.failureAckAt ?: JSONObject.NULL)
    fun scan(s: TemporaryScan): JSONObject = JSONObject().put("captureId", s.captureId).put("orderScanSeq", s.orderScanSeq.toString())
        .put("barcode", s.barcode).put("baleId", s.baleId).put("deliveryCode", s.deliveryCode).put("model", s.model).put("name", s.name)
        .put("rawWeight", s.rawWeight).put("weightGrams", s.weightGrams.toString()).put("expectedDispatchCycle", s.expectedDispatchCycle.toString())
        .put("savedAt", s.savedAt).put("localSeq", s.localSeq.toString()).put("status", s.status)
}
