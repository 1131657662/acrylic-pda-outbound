package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.data.DeviceAuditTrail
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.submission.SubmissionRecords
import com.acrylic.outbound.pda.temporary.TemporarySubmission
import com.acrylic.outbound.pda.temporary.TemporarySubmissionRecords
import com.acrylic.outbound.pda.temporary.TemporaryCapture
import com.acrylic.outbound.pda.retention.AuditArchive
import com.acrylic.outbound.pda.work.WorkCapture
import org.json.JSONObject
import java.util.UUID

class LocalE2aSnapshot(private val db: PdaDatabase) {
    fun save(id: String, frozen: TemporarySubmission, boundary: E2aDeviceCoverage.Boundary,
        archives: List<AuditArchive>) {
        check(db.inTransaction())
        val stop = checkNotNull(db.localE2aStops().get(id))
        check(stop.submissionId == frozen.id && db.localE2aObjects().count(id) == 0L &&
            stop.captureHighWatermark == boundary.captureHighWatermark &&
            stop.auditThroughSeq == boundary.auditThroughSeq)
        val sink = Sink(db, id)
        saveCaptures(stop, sink)
        saveAudit(stop, sink)
        LocalE2aArchiveSnapshot.originals(stop, archives).forEach(sink::appendObject)
        sink.append("FROZEN", frozen.id, stop.auditThroughSeq, "FROZEN",
            frozen.payload.toByteArray(Charsets.UTF_8))
        verify(stop, frozen)
    }

    private fun saveCaptures(stop: LocalE2aStop, sink: Sink) {
        val device = stop.freezeDeviceId
        var captureSeq = 0L
        while (captureSeq < stop.captureHighWatermark) {
            val formal = db.captures().devicePage(device, captureSeq)
            val temporary = db.temporaryScans().deviceCaptures(device, captureSeq)
            val rows = (formal.map { it.context.deviceCaptureSeq to it } +
                temporary.map { it.deviceCaptureSeq to it }).sortedBy { it.first }.take(128)
            check(rows.isNotEmpty())
            val through = rows.last().first
            val formalScans = db.scans().devicePage(device, captureSeq, through).associateBy { it.captureId }
            val temporaryScans = db.temporaryScans().deviceScans(device, captureSeq, through)
                .associateBy { it.captureId }
            for ((sequence, capture) in rows) {
                check(sequence == captureSeq + 1 && sequence <= stop.captureHighWatermark)
                when (capture) {
                    is WorkCapture -> {
                        sink.append("CAPTURE", capture.id, sequence, capture.state, SubmissionRecords.capture(capture))
                        formalScans[capture.id]?.let {
                            sink.append("SCAN", it.captureId, it.localSeq, it.status, SubmissionRecords.scan(it))
                        }
                    }
                    is TemporaryCapture -> {
                        sink.append("CAPTURE", capture.id, sequence, capture.state,
                            TemporarySubmissionRecords.capture(capture))
                        temporaryScans[capture.id]?.let {
                            sink.append("SCAN", it.captureId, it.localSeq, it.status,
                                TemporarySubmissionRecords.scan(it))
                        }
                    }
                }
                captureSeq = sequence
            }
        }
    }

    private fun saveAudit(stop: LocalE2aStop, sink: Sink) {
        var auditSeq = 0L
        val trail = DeviceAuditTrail(db)
        while (auditSeq < stop.auditThroughSeq) {
            val page = trail.page(stop.freezeDeviceId, auditSeq, 128)
            check(page.isNotEmpty())
            for (event in page) {
                val sequence = event.getString("localSeq").toLong()
                check(sequence == auditSeq + 1 && sequence <= stop.auditThroughSeq)
                sink.append("AUDIT", event.getString("id"), sequence, event.getString("type"), event)
                auditSeq = sequence
            }
        }
    }

    private class Sink(private val db: PdaDatabase, private val id: String) {
        private var total = 0L

        fun append(kind: String, recordId: String, sequence: Long, status: String, record: JSONObject) =
            append(kind, recordId, sequence, status, SubmissionJson.canonical(record).toByteArray(Charsets.UTF_8))

        fun append(kind: String, recordId: String, sequence: Long, status: String, raw: ByteArray) {
            check(raw.isNotEmpty() && raw.size <= 33554432 && sequence > 0)
            total = Math.addExact(total, raw.size.toLong())
            check(total <= 134217728)
            db.localE2aObjects().insert(LocalE2aObject(id, kind, recordId, UUID.randomUUID().toString(),
                sequence, status, E2aWire.sha(raw), raw))
        }

        fun appendObject(row: LocalE2aObject) {
            val key = checkNotNull(row.signingKeyJson)
            total = Math.addExact(total, row.rawBytes.size.toLong() + key.toByteArray(Charsets.UTF_8).size)
            check(total <= 134217728)
            db.localE2aObjects().insert(row)
        }
    }

    fun verify(stop: LocalE2aStop, frozen: TemporarySubmission) {
        check(db.inTransaction() && stop.submissionId == frozen.id)
        val id = stop.clientOrderId
        val original = checkNotNull(db.localE2aObjects().frozen(id, frozen.id))
        check(original.deviceSeq == stop.auditThroughSeq && original.recordStatus == "FROZEN")
        check(original.rawBytes.contentEquals(frozen.payload.toByteArray(Charsets.UTF_8)))
        check(original.bytesSha256 == frozen.sha256)
        val capture = verifyKind(stop, "CAPTURE", stop.captureHighWatermark)
        val scan = verifyKind(stop, "SCAN", stop.auditThroughSeq)
        val audit = verifyKind(stop, "AUDIT", stop.auditThroughSeq)
        val archive = LocalE2aArchiveSnapshot.verify(db, stop)
        check(scan.first == stop.scanSnapshotCount)
        val count = Math.addExact(Math.addExact(capture.first, scan.first),
            Math.addExact(audit.first, archive.first))
        val bytes = Math.addExact(Math.addExact(capture.second, scan.second),
            Math.addExact(Math.addExact(audit.second, archive.second), validate(original).toLong()))
        check(bytes <= 134217728 && db.localE2aObjects().count(id) == count + 1)
    }

    private fun verifyKind(stop: LocalE2aStop, kind: String, through: Long): Pair<Long, Long> {
        var previous = 0L
        var count = 0L
        var bytes = 0L
        while (true) {
            val rows = db.localE2aObjects().page(stop.clientOrderId, kind, previous)
            if (rows.isEmpty()) break
            for (row in rows) {
                check(row.deviceSeq > previous && row.deviceSeq <= through)
                if (kind != "SCAN") check(row.deviceSeq == previous + 1)
                bytes = Math.addExact(bytes, validate(row).toLong())
                count++
                previous = row.deviceSeq
            }
        }
        if (kind != "SCAN") check(previous == through)
        return count to bytes
    }

    private fun validate(row: LocalE2aObject): Int {
        check(row.signingKeyJson == null)
        check(row.rawBytes.isNotEmpty() && row.rawBytes.size <= 33554432 &&
            E2aWire.sha(row.rawBytes) == row.bytesSha256)
        E2aItemValidation.uuid(row.objectId)
        E2aItemValidation.uuid(row.recordId)
        val record = JSONObject(row.rawBytes.toString(Charsets.UTF_8))
        check(SubmissionJson.canonical(record).toByteArray(Charsets.UTF_8).contentEquals(row.rawBytes))
        if (row.kind != "FROZEN") {
            check(record.getString(if (row.kind == "SCAN") "captureId" else "id") == row.recordId)
            check(record.getString(when (row.kind) {
                "CAPTURE" -> "deviceCaptureSeq"
                "SCAN" -> "localSeq"
                else -> "localSeq"
            }).toLong() == row.deviceSeq)
            check(record.getString(when (row.kind) {
                "CAPTURE" -> "state"
                "SCAN" -> "status"
                else -> "type"
            }) == row.recordStatus)
        }
        return row.rawBytes.size
    }
}
