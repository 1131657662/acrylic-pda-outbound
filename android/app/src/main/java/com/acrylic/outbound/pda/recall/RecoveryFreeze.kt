package com.acrylic.outbound.pda.recall

import com.acrylic.outbound.pda.work.*
import com.acrylic.outbound.pda.submission.*
import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONArray
import java.time.Instant
import java.util.UUID

class RecoveryFreeze(private val store: WorkStore, private val now: () -> Instant) {
    fun unresolved(assignment: String) = store.db.captures().page(assignment, 0, Int.MAX_VALUE).filter {
        it.state in setOf("RECEIVED", "WAITING_CONFIRM", "CONFIRM_REQUESTED") ||
            (it.state in setOf("INVALID", "BUSINESS_REJECTED") && it.failureAckAt == null)
    }
    fun fingerprint(rows: List<WorkCapture>): String = JwtVerifier.sha(
        SubmissionJson.canonical(JSONArray(rows.map(SubmissionRecords::capture))).toByteArray(Charsets.UTF_8))

    fun freeze(assignment: String, reviewedFingerprint: String): LocalRecall {
        lateinit var result: LocalRecall
        store.db.runInTransaction {
            store.access.identity(store.prepared(assignment))
            val old = checkNotNull(store.db.recalls().get(assignment)) { "请先联网取得召回申请" }
            if (old.payload != null) { result = old; return@runInTransaction }
            check(store.db.work().order(assignment)?.state == "RECALL_PENDING") { "必须先持久停扫" }
            val rows = unresolved(assignment)
            check(fingerprint(rows) == reviewedFingerprint) { "未保存记录已变化，请重新逐项核对" }
            val at = now().toString(); rows.forEach { resolve(it, at) }; store.verifyTotals(assignment)
            val id = UUID.randomUUID().toString(); store.event(assignment, null, "RECALL_STOPPED", id)
            val payload = SubmissionJson.canonical(RecoveryBody(store).create(old, id, at))
            val bytes = payload.toByteArray(Charsets.UTF_8)
            check(bytes.size <= 32 * 1024 * 1024) { "恢复证据超过32MiB，请保留资料联系主管" }
            result = old.copy(packageId = id, payload = payload, sha256 = JwtVerifier.sha(bytes), state = "RECOVERY_FROZEN")
            store.db.recalls().update(result)
        }
        return result
    }
    private fun resolve(c: WorkCapture, at: String) {
        val assignment = c.context.assignmentId; val reason = "收到召回，操作员核对本包未保存"
        if (c.state in setOf("RECEIVED", "WAITING_CONFIRM", "CONFIRM_REQUESTED")) {
            check(store.db.captures().progress(c.id, "BUSINESS_REJECTED", reason, c.confirmedAt) == 1)
            store.event(assignment, c.id, "BUSINESS_REJECTED", reason)
        }
        if (store.db.captures().acknowledge(c.id, at) == 1) store.event(assignment, c.id, "FAILURE_ACKNOWLEDGED", reason)
    }
}
