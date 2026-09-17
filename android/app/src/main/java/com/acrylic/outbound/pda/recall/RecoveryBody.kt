package com.acrylic.outbound.pda.recall

import com.acrylic.outbound.pda.work.*
import com.acrylic.outbound.pda.submission.SubmissionRecords
import org.json.*

class RecoveryBody(private val store: WorkStore) {
    fun create(local: LocalRecall, id: String, closed: String): JSONObject {
        val order = checkNotNull(store.db.work().order(local.assignmentId)); val prepared = store.prepared(local.assignmentId)
        val a = JSONObject(prepared.assignmentJson); val plan = JSONObject(prepared.planJson)
        val captures = store.db.captures().page(local.assignmentId, 0, Int.MAX_VALUE)
        val scans = store.db.scans().page(local.assignmentId, 0, Int.MAX_VALUE)
        val audit = store.db.work().deviceEvents(prepared.deviceId)
        check(audit.map { it.localSeq } == (1L..audit.size.toLong()).toList()) { "本设备审计不连续，请保留数据联系主管" }
        return JSONObject().put("schemaVersion", 2).put("recoveryPackageId", id).put("changeRequestId", local.id)
            .put("assignmentId", a.getString("id")).put("orderId", prepared.orderId).put("assignmentEpoch", a.getString("epoch"))
            .put("orderRevision", a.getInt("revision")).put("inventorySnapshotId", a.getString("inventorySnapshotId"))
            .put("policyVersion", plan.getJSONObject("policy").getString("version"))
            .put("autoDeductInventory", plan.getJSONObject("policy").getBoolean("autoDeductInventory"))
            .put("startedAt", order.startedAt ?: JSONObject.NULL).put("closedAt", closed).put("continuationId", store.db.continuations().current(local.assignmentId)?.id ?: JSONObject.NULL)
            .put("workRound", (store.db.continuations().current(local.assignmentId)?.workRound ?: 1).toString())
            .put("shortShipmentReason", JSONObject.NULL)
            .put("sourceRecoveryEpoch", a.getString("serverRecoveryEpoch")).put("freezeDeviceId", prepared.deviceId)
            .put("expectedCount", order.actualCount.toString()).put("expectedWeightGrams", order.weightGrams.toString())
            .put("captureHighWatermark", (order.nextOrderScanSeq - 1).toString()).put("unresolvedCount", "0")
            .put("captureSummary", summary(captures, scans).put("saved", order.actualCount.toString())).put("captures", JSONArray(captures.map(SubmissionRecords::capture)))
            .put("dispositions", store.db.dispositions().proofs(local.assignmentId))
            .put("scans", JSONArray(scans.map(SubmissionRecords::scan)))
            .put("events", JSONArray(store.db.work().events(local.assignmentId).map(SubmissionRecords::event)))
            .put("auditEvents", JSONArray(audit.map(SubmissionRecords::event)))
            .put("auditCoverage", JSONArray().put(JSONObject().put("originDeviceId", prepared.deviceId)
                .put("afterSeq", "0").put("throughSeq", audit.last().localSeq.toString())))
    }
    private fun summary(captures: List<WorkCapture>, scans: List<WorkScan>) = JSONObject()
        .put("received", captures.size.toString()).put("saved", scans.count { it.status == "ACTIVE" }.toString())
        .put("voided", scans.count { it.status == "VOID" }.toString()).put("pending", "0")
        .put("invalid", captures.count { it.state == "INVALID" }.toString())
        .put("businessRejected", captures.count { it.state == "BUSINESS_REJECTED" }.toString())
        .put("discarded", captures.count { it.state == "DISCARDED" }.toString())
}
