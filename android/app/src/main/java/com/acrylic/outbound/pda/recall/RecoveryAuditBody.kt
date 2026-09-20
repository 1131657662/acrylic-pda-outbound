package com.acrylic.outbound.pda.recall

import com.acrylic.outbound.pda.work.WorkStore
import com.acrylic.outbound.pda.retention.AuditArchiveVerifier
import com.acrylic.outbound.pda.submission.SubmissionRecords
import org.json.*

class RecoveryAuditBody(private val store: WorkStore) {
    fun attach(body: JSONObject, device: String, epoch: String, warehouse: String): JSONObject {
        val rows = AuditArchiveVerifier.prefix(store.db.auditArchives().rows(epoch, device), warehouse)
        val after = rows.lastOrNull()?.throughSeq ?: 0L
        val all = store.db.work().deviceEvents(device)
        rows.forEach { row ->
            val local = all.filter { it.localSeq > row.afterSeq && it.localSeq <= row.throughSeq }
            if (local.isNotEmpty()) {
                val payload = AuditArchiveVerifier.payload(device, row.afterSeq, row.throughSeq, JSONArray(local.map(SubmissionRecords::event)))
                check(AuditArchiveVerifier.digest(payload) == row.payloadSha256) { "本机审计与归档凭据不一致，请保留资料核对" }
            }
        }
        val events = all.filter { it.localSeq > after }
        var next = after
        events.forEach { check(it.localSeq == ++next) { "本设备审计尾部不连续，请保留数据联系主管" } }
        check(events.isNotEmpty()) { "缺少原机停止事件" }
        if (rows.isNotEmpty()) body.put("auditArchives", JSONArray(rows.map { JSONObject(checkNotNull(it.proofJson)) }))
        return body.put("auditEvents", JSONArray(events.map(SubmissionRecords::event)))
            .put("auditCoverage", JSONArray().put(JSONObject().put("originDeviceId", device)
                .put("afterSeq", after.toString()).put("throughSeq", next.toString())))
    }
}
