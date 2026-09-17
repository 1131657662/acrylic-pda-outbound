package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.work.*
import com.acrylic.outbound.pda.network.PdaSession
import com.acrylic.outbound.pda.submission.*
import org.json.*

class ContinuationRepository(private val store: WorkStore, private val session: PdaSession, private val resumed: (String) -> Unit) {
    @Synchronized fun activate(root: String) {
        val local = store.db.recalls().get(root)
        if (local == null) { check(store.db.continuations().current(root) != null); resumed(root); return }
        check(local.state == "RECALLED") { "须先上传完整恢复包并核验停止证明" }
        val prepared = store.prepared(root); store.access.identity(prepared)
        val old = JSONObject(prepared.assignmentJson)
        val remote = session.call("pda/assignments/${old.getString("id")}/change-request").getJSONObject("request")
        val ticket = remote.optJSONObject("continuation") ?: error("主管尚未申请并批准续作")
        check(ticket.getString("status") in setOf("APPROVED", "ACTIVATED")) { "须等待主管核验批准" }
        val body = JSONObject(checkNotNull(local.payload)); val retainedHash = ContinuationVerifier.retainedHash(body, DispositionRecords.rows(ticket))
        val command = JSONObject().put("payloadSha256", local.sha256).put("retainedManifestSha256", retainedHash)
            .put("nextOrderScanSeq", Math.addExact(body.getString("captureHighWatermark").toLong(), 1).toString()).put("planSha256", old.getString("planSha256"))
        val response = session.call("pda/continuations/${ticket.getString("continuationId")}/activate", command, old.getString("serverRecoveryEpoch"))
        val assignment = ContinuationVerifier.verify(local, response, JSONObject(prepared.signingKeyJson))
        check(com.acrylic.outbound.pda.security.JwtVerifier.sha(prepared.planJson.toByteArray(Charsets.UTF_8)) == old.getString("planSha256")) { "本机固定计划损坏" }
        check(store.db.preparations().count(root) == prepared.rowCount) { "本机库存资料不完整" }
        check(assignment.getString("planSha256") == old.getString("planSha256") && assignment.getString("inventorySha256") == old.getString("inventorySha256")) { "固定资料摘要发生变化" }
        store.access.authorize(prepared.copy(assignmentJson = assignment.toString()))
        install(root, local, response, assignment); resumed(root)
    }
    private fun install(root: String, local: com.acrylic.outbound.pda.recall.LocalRecall, response: JSONObject, assignment: JSONObject) {
        store.db.runInTransaction {
            if (store.db.continuations().get(response.getString("continuationId")) != null) return@runInTransaction
            check(store.db.recalls().header(root)?.id == local.id && store.db.work().order(root)?.state == "RECALLED")
            val body = JSONObject(checkNotNull(local.payload)); verifyFacts(root, body); store.verifyTotals(root)
            val work = checkNotNull(store.db.work().order(root)); check(work.nextOrderScanSeq == response.getString("nextOrderScanSeq").toLong())
            val previous = store.db.submissions().forOrder(root)
            check(previous != null && previous.id == response.getString("previousSubmissionId")) { "缺少原冻结批次，请保留资料核对" }
            check(previous.state != "SYNCED") { "已成功批次不能续作" }
            DispositionInstall(store).apply(root, body, response)
            store.db.submissions().supersede(root); store.db.continuations().supersede(root)
            store.db.continuations().insert(LocalContinuation(response.getString("continuationId"), root, assignment.toString(), response.toString(),
                response.getString("workRound").toLong(), response.getString("nextOrderScanSeq").toLong(), local.id))
            store.db.recalls().supersede(root); check(store.db.work().continueWork(root) == 1)
            store.event(root, null, "CONTINUATION_ACTIVATED", response.getString("continuationId"))
        }
    }
    private fun verifyFacts(root: String, body: JSONObject) {
        val captures = JSONArray(store.db.captures().page(root, 0, Int.MAX_VALUE).map(SubmissionRecords::capture))
        val scans = JSONArray(store.db.scans().page(root, 0, Int.MAX_VALUE).map(SubmissionRecords::scan))
        val events = JSONArray(store.db.work().events(root).map(SubmissionRecords::event))
        for ((key, records) in listOf("captures" to captures, "scans" to scans, "events" to events))
            check(SubmissionJson.canonical(records) == SubmissionJson.canonical(body.getJSONArray(key))) { "本机原记录与核验恢复包不一致：$key" }
        check(store.db.captures().pendingCount(root) == 0 && store.db.captures().failure(root) == null)
    }
}
