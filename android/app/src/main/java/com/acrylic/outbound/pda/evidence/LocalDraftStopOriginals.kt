package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.temporary.TemporaryAuditRecord
import com.acrylic.outbound.pda.temporary.TemporaryDraft
import com.acrylic.outbound.pda.temporary.TemporarySubmissionRecords
import com.acrylic.outbound.pda.temporary.TemporaryWorkStore
import org.json.JSONObject

/** Only this stopped source is immutable; other sources may continue after the captured boundary. */
internal class LocalDraftStopOriginals(store: TemporaryWorkStore, private val draft: TemporaryDraft) {
    private val expected = mutableMapOf<String, String>()
    private val snapshotCaptures = hashSetOf<String>()

    init {
        val captures = store.db.temporaryScans().captures(draft.clientOrderId)
        val scans = store.db.temporaryScans().allScans(draft.clientOrderId)
        check(captures.size.toLong() == draft.nextScanSeq - 1)
        check(captures.filter { it.state == "COMMITTED" }.map { it.id }.toSet() == scans.map { it.captureId }.toSet())
        captures.forEach { expected["CAPTURE/${it.id}"] = SubmissionJson.canonical(TemporarySubmissionRecords.capture(it)) }
        scans.forEach { expected["SCAN/${it.captureId}"] = SubmissionJson.canonical(TemporarySubmissionRecords.scan(it)) }
        store.db.temporaryScans().events(draft.clientOrderId).forEach {
            expected["AUDIT/${it.id}"] = SubmissionJson.canonical(
                TemporaryAuditRecord(it, draft.creatorDeviceId, draft.authorizationId, draft.operatorId).json())
        }
    }

    fun compare(row: LocalDraftStopItem) {
        val raw = row.rawBytes.toString(Charsets.UTF_8)
        val body = JSONObject(raw)
        val own = when (row.kind) {
            "CAPTURE" -> (body.optString("clientOrderId") == draft.clientOrderId).also {
                if (it) snapshotCaptures.add(row.recordId)
            }
            "SCAN" -> row.recordId in snapshotCaptures
            "AUDIT" -> body.optJSONObject("source")?.optString("clientOrderId") == draft.clientOrderId
            else -> false
        }
        if (own) check(expected.remove("${row.kind}/${row.recordId}") == raw) { "原停止输入或审计已变化" }
    }

    fun complete() { check(expected.isEmpty()) { "原停止资料不完整" } }
}
