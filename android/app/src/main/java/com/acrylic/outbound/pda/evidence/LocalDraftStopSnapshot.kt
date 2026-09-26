package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.data.DeviceAuditTrail
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.retention.AuditArchiveVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.submission.SubmissionRecords
import com.acrylic.outbound.pda.temporary.TemporarySubmissionRecords
import org.json.JSONObject

internal class LocalDraftStopSnapshot(private val db: PdaDatabase, private val stop: LocalDraftStop) {
    private val metadata = JSONObject(stop.metadataJson)
    private val draft = metadata.getJSONObject("draft")
    private val device = draft.getString("creatorDeviceId")
    private val boundary = metadata.getString("captureHighWatermark").toLong()
    private val through = metadata.getString("auditThroughSeq").toLong()
    private val digest = LocalDraftStopDigest(stop.metadataJson)

    fun save() {
        check(db.inTransaction() && db.localDraftStops().count(stop.clientOrderId) == 0L)
        captures(); audits(); archives()
        check(db.localDraftStops().seal(stop.clientOrderId, digest.finish(), digest.count, digest.bytes) == 1)
    }

    private fun captures() {
        var after = 0L
        while (after < boundary) {
            val formal = db.captures().devicePage(device, after).map { SubmissionRecords.capture(it) }
            val temporary = db.temporaryScans().deviceCaptures(device, after)
                .map { TemporarySubmissionRecords.capture(it) }
            val rows = (formal + temporary).sortedBy { it.getString("deviceCaptureSeq").toLong() }.take(128)
            check(rows.isNotEmpty())
            val last = rows.last().getString("deviceCaptureSeq").toLong()
            val scans = db.scans().devicePage(device, after, last).map { SubmissionRecords.scan(it) } +
                db.temporaryScans().deviceScans(device, after, last).map { TemporarySubmissionRecords.scan(it) }
            val byCapture = scans.associateBy { it.getString("captureId") }
            check(byCapture.size == scans.size)
            for (row in rows) {
                val sequence = row.getString("deviceCaptureSeq").toLong()
                check(sequence == after + 1 && sequence <= boundary)
                check((row.getString("state") == "COMMITTED") == byCapture.containsKey(row.getString("id")))
                append("CAPTURE", row.getString("id"), sequence, row.getString("state"), row)
                byCapture[row.getString("id")]?.let {
                    append("SCAN", it.getString("captureId"), it.getString("localSeq").toLong(), it.getString("status"), it)
                }
                after = sequence
            }
        }
    }

    private fun audits() {
        var after = 0L
        val trail = DeviceAuditTrail(db)
        while (after < through) {
            val rows = trail.page(device, after, 128)
            check(rows.isNotEmpty())
            for (row in rows) {
                val sequence = row.getString("localSeq").toLong()
                check(sequence == after + 1 && sequence <= through)
                append("AUDIT", row.getString("id"), sequence, row.getString("type"), row)
                after = sequence
            }
        }
    }

    private fun archives() {
        val epoch = metadata.getString("sourceRecoveryEpoch").toLong()
        val rows = db.auditArchives().completed(device, epoch)
        check(rows.size <= 4096)
        for (row in rows) {
            check(row.throughSeq in 1..through)
            val proof = JSONObject(checkNotNull(row.proofJson))
            val key = JSONObject(checkNotNull(row.signingKeyJson))
            check(SubmissionJson.canonical(proof) == row.proofJson && SubmissionJson.canonical(key) == row.signingKeyJson)
            AuditArchiveVerifier.verify(row, proof, key, draft.getString("warehouseId"))
            val id = LocalE2aArchiveSnapshot.recordId(device, row.recoveryEpoch.toLong(), row.afterSeq)
            append("ARCHIVE", id, row.throughSeq, "ARCHIVED",
                JSONObject().put("originalProof", row.proofJson).put("originalSigningKey", row.signingKeyJson))
        }
    }

    private fun append(kind: String, id: String, sequence: Long, status: String, value: JSONObject) {
        val raw = SubmissionJson.canonical(value).toByteArray(Charsets.UTF_8)
        val row = LocalDraftStopItem(stop.clientOrderId, digest.count + 1, kind, id, sequence, status, E2aWire.sha(raw), raw)
        digest.append(row)
        db.localDraftStops().insertItem(row)
    }
}
