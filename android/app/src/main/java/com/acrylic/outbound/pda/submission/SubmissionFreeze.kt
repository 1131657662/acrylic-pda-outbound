package com.acrylic.outbound.pda.submission

import com.acrylic.outbound.pda.work.*
import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.*
import java.time.Instant
import java.util.UUID

class SubmissionFreeze(private val store: WorkStore, private val now: () -> Instant) {
    fun freeze(assignment: String, reason: String): LocalSubmission {
        lateinit var result: LocalSubmission
        store.db.runInTransaction {
            store.access.identity(store.prepared(assignment))
            val old = store.db.submissions().forOrder(assignment)
            if (old != null) { result = old; return@runInTransaction }
            com.acrylic.outbound.pda.sync.SyncWorkGuard(store.db).requireAllowed(store.prepared(assignment))
            val order = checkNotNull(store.db.work().order(assignment)); store.verifyTotals(assignment)
            check(order.state == "SCANNING" && order.actualCount > 0) { "必须有有效装车记录才能结单" }
            check(store.db.captures().pendingCount(assignment) == 0 && store.db.captures().failure(assignment) == null) { "请先处理全部未决输入和错误" }
            val short = store.db.work().lines(assignment).any { it.actualCount < it.plannedCount }
            check(reason.trim().length <= 500 && (!short || reason.isNotBlank())) { "短装须填写原因并由主管批准" }
            val id = UUID.randomUUID().toString(); val closed = now().toString()
            store.event(assignment, null, "FROZEN", id)
            val body = body(order, id, reason.trim(), closed)
            val payload = SubmissionJson.canonical(body)
            check(payload.toByteArray(Charsets.UTF_8).size <= 32 * 1024 * 1024) { "完整证据超过32MiB，请保留资料联系管理员" }
            result = LocalSubmission(id, assignment, payload, JwtVerifier.sha(payload.toByteArray(Charsets.UTF_8)))
            store.db.submissions().insert(result); check(store.db.work().freeze(assignment) == 1)
        }
        return result
    }
    private fun body(order: WorkOrder, id: String, reason: String, closed: String): JSONObject {
        val prepared = store.prepared(order.assignmentId); val a = JSONObject(prepared.assignmentJson)
        val continuation = store.db.continuations().current(order.assignmentId)
        val plan = JSONObject(prepared.planJson); val captures = store.db.captures().page(order.assignmentId, 0, Int.MAX_VALUE)
        val scans = store.db.scans().page(order.assignmentId, 0, Int.MAX_VALUE)
        return JSONObject().put("schemaVersion", 2).put("submissionId", id).put("assignmentId", a.getString("id"))
            .put("assignmentEpoch", a.getString("epoch")).put("orderRevision", a.getInt("revision"))
            .put("inventorySnapshotId", a.getString("inventorySnapshotId")).put("policyVersion", plan.getJSONObject("policy").getString("version"))
            .put("autoDeductInventory", plan.getJSONObject("policy").getBoolean("autoDeductInventory"))
            .put("startedAt", order.startedAt ?: JSONObject.NULL).put("closedAt", closed).put("shortShipmentReason", reason.ifEmpty { null } ?: JSONObject.NULL)
            .put("expectedCount", order.actualCount.toString()).put("expectedWeightGrams", order.weightGrams.toString())
            .put("captureHighWatermark", (order.nextOrderScanSeq - 1).toString()).put("captureSummary", summary(captures, scans).put("saved", order.actualCount.toString()))
            .put("dispositions", store.db.dispositions().proofs(order.assignmentId))
            .put("source", JSONObject().put("kind", "FORMAL").put("sourceOrderId", prepared.orderId))
            .put("freezeDeviceId", prepared.deviceId).put("workRound", (continuation?.workRound ?: 1).toString()).put("continuationId", continuation?.id ?: JSONObject.NULL)
            .put("sourceRecoveryEpoch", a.getString("serverRecoveryEpoch"))
            .put("captures", JSONArray(captures.map(SubmissionRecords::capture))).put("scans", JSONArray(scans.map(SubmissionRecords::scan)))
            .put("events", JSONArray(store.db.work().events(order.assignmentId).map(SubmissionRecords::event)))
    }
    private fun summary(captures: List<WorkCapture>, scans: List<WorkScan>) = JSONObject()
        .put("received", captures.size.toString()).put("saved", scans.count { it.status == "ACTIVE" }.toString())
        .put("voided", scans.count { it.status == "VOID" }.toString()).put("pending", "0")
        .put("invalid", captures.count { it.state == "INVALID" }.toString())
        .put("businessRejected", captures.count { it.state == "BUSINESS_REJECTED" }.toString())
        .put("discarded", captures.count { it.state == "DISCARDED" }.toString())
}
