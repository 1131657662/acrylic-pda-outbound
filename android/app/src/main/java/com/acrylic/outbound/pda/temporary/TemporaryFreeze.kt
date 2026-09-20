package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.update.UpdateGate
import org.json.*
import java.util.UUID
import java.util.concurrent.Callable

class TemporaryFreeze(private val store: TemporaryWorkStore) {
    fun freeze(id: String, reason: String): TemporarySubmission = UpdateGate.business {
        store.load(id)
        store.db.runInTransaction(Callable {
            val draft = store.check(id, false)
            store.db.temporarySubmissions().forOrder(id)?.let { return@Callable it }
            store.requireNoOtherInput(id)
            val dao = store.db.temporaryScans(); check(draft.state == "SCANNING" && dao.total(id).count > 0) { "须有有效装车记录才能结单" }
            check(dao.pending(id) == null && dao.failure(id) == null) { "请先处理本单全部未决输入和失败" }
            val totals = dao.products(id).associateBy { it.deliveryCode }
            val short = store.db.temporaryDrafts().lines(id).any { (totals[it.deliveryCode]?.count ?: 0) < it.plannedCount }
            check(reason.trim().length <= 500 && (!short || reason.isNotBlank())) { "短装须填写原因，等待专项审批" }
            val submission = UUID.randomUUID().toString(); store.event(draft, null, "FROZEN", submission)
            val payload = SubmissionJson.canonical(body(draft, submission, reason.trim()))
            check(payload.toByteArray(Charsets.UTF_8).size <= 32 * 1024 * 1024) { "冻结正文超过32MiB，请保留资料联系主管" }
            TemporarySubmission(submission, id, payload, JwtVerifier.sha(payload.toByteArray(Charsets.UTF_8))).also {
                store.db.temporarySubmissions().insert(it); store.db.temporaryDrafts().update(draft.copy(state = "FROZEN"))
            }
        })
    }
    private fun body(draft: TemporaryDraft, id: String, reason: String): JSONObject {
        val source = store.source(draft.clientOrderId); val manifest = source.manifest; val dao = store.db.temporaryScans()
        val captures = dao.captures(draft.clientOrderId); val scans = dao.allScans(draft.clientOrderId); val total = dao.total(draft.clientOrderId)
        val policy = JSONObject(String(TemporaryParts(store.db).read(draft.authorizationId, manifest).getValue("policy"), Charsets.UTF_8))
        return JSONObject().put("schemaVersion", 2).put("submissionId", id).put("authorizationId", draft.authorizationId)
            .put("authorizationEpoch", manifest.getJSONObject("identity").getString("authorizationEpoch"))
            .put("assignmentId", JSONObject.NULL).put("continuationId", JSONObject.NULL).put("workRound", "1")
            .put("source", JSONObject().put("kind", "TEMP").put("creatorDeviceId", draft.creatorDeviceId).put("clientOrderId", draft.clientOrderId))
            .put("freezeDeviceId", draft.creatorDeviceId).put("sourceRecoveryEpoch", source.request.recoveryEpoch)
            .put("temporaryNo", draft.temporaryNo).put("temporaryOrder", TemporaryPlanDigest.body(draft, store.db.temporaryDrafts().lines(draft.clientOrderId)))
            .put("planSha256", draft.planSha256).put("orderRevision", draft.revision.toString())
            .put("inventorySnapshotId", manifest.getString("inventorySnapshotId")).put("policyVersion", manifest.getString("policyVersion"))
            .put("autoDeductInventory", policy.getBoolean("autoDeductInventory")).put("closedAt", store.connection.now().toString())
            .put("shortShipmentReason", reason.ifBlank { null } ?: JSONObject.NULL).put("expectedCount", total.count.toString()).put("expectedWeightGrams", total.grams.toString())
            .put("captureHighWatermark", (draft.nextScanSeq - 1).toString()).put("dispositions", JSONArray())
            .put("captures", JSONArray(captures.map(TemporarySubmissionRecords::capture))).put("scans", JSONArray(scans.map(TemporarySubmissionRecords::scan)))
            .put("captureSummary", summary(captures, scans))
            .put("events", JSONArray(dao.events(draft.clientOrderId).map { TemporaryAuditRecord(it, draft.creatorDeviceId, draft.authorizationId, draft.operatorId).json() }))
    }
    private fun summary(captures: List<TemporaryCapture>, scans: List<TemporaryScan>) = JSONObject()
        .put("received", captures.size.toString()).put("saved", scans.count { it.status == "ACTIVE" }.toString())
        .put("voided", scans.count { it.status == "VOID" }.toString()).put("pending", "0")
        .put("invalid", captures.count { it.state == "INVALID" }.toString()).put("businessRejected", captures.count { it.state == "BUSINESS_REJECTED" }.toString())
        .put("discarded", captures.count { it.state == "DISCARDED" }.toString())
}
