package com.acrylic.m71demo.export

import com.acrylic.m71demo.data.*
import org.json.JSONObject

object JsonRows {
    fun capture(c: InputCapture): JSONObject = context(c.id, c.context).put("state", c.state)
        .put("error", c.error).put("confirmedAtNs", c.confirmedAtNs ?: JSONObject.NULL)
        .put("failureAcknowledged", c.failureAcknowledged)

    fun scan(s: ScanRecord): JSONObject = context(s.id, s.context)
        .put("weightGrams", s.weightGrams.toString()).put("rawWeight", s.rawWeight)
        .put("deliveryCode", s.deliveryCode).put("status", s.status).put("localSeq", s.localSeq)
        .put("savedAt", s.savedAt).put("confirmedAtNs", s.confirmedAtNs)

    fun audit(a: AuditRecord): JSONObject = JSONObject().put("id", a.id).put("originDeviceId", a.originDeviceId)
        .put("localSeq", a.localSeq).put("orderId", a.orderId).put("captureId", a.captureId ?: JSONObject.NULL)
        .put("type", a.type).put("reason", a.reason).put("wallMs", a.occurredAt)
        .put("elapsedNs", a.elapsedNs).put("bootSessionId", a.bootSessionId)

    private fun context(id: String, c: CaptureContext): JSONObject = JSONObject()
        .put("captureId", id).put("orderId", c.orderId).put("originDeviceId", c.originDeviceId)
        .put("orderScanSeq", c.orderScanSeq).put("deviceCaptureSeq", c.deviceCaptureSeq)
        .put("barcode", c.barcode).put("rawInput", c.rawInput).put("inputSource", c.inputSource)
        .put("receivedAt", c.receivedAt).put("receivedElapsedNs", c.receivedElapsedNs)
        .put("bootSessionId", c.bootSessionId).put("snapshotId", c.snapshotId)
        .put("expectedDispatchCycle", c.expectedDispatchCycle ?: JSONObject.NULL)
        .put("sourceRecoveryEpoch", c.sourceRecoveryEpoch).put("authorizationId", c.authorizationId)
        .put("operatorId", c.operatorId).put("orderRevision", c.orderRevision)
        .put("assignmentEpoch", c.assignmentEpoch).put("inputRevision", c.inputRevision)
        .put("confirmationPolicy", c.confirmationPolicy)
}
