package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.data.PdaDatabase
import org.json.JSONObject

/** Full raw audit coverage is selected once; archive proofs stay local without overlapping pages. */
internal class E2aSnapshotReferences(private val db: PdaDatabase, private val stop: LocalE2aStop) {
    fun all(): Sequence<JSONObject> = sequence {
        check(db.inTransaction())
        for (kind in listOf("CAPTURE", "SCAN", "AUDIT")) {
            var after = 0L
            while (true) {
                val rows = db.localE2aObjects().page(stop.clientOrderId, kind, after)
                if (rows.isEmpty()) break
                for (row in rows) {
                    check(row.deviceSeq > after)
                    yield(reference(row))
                    after = row.deviceSeq
                }
            }
        }
        yield(reference(checkNotNull(db.localE2aObjects().frozen(stop.clientOrderId, stop.submissionId))))
    }

    private fun reference(row: LocalE2aObject): JSONObject {
        check(E2aWire.sha(row.rawBytes) == row.bytesSha256)
        val scan = row.kind == "SCAN"
        val record = JSONObject(row.rawBytes.toString(Charsets.UTF_8))
        val kind = if (row.kind == "FROZEN") "STOP" else row.kind
        val objectKind = when (row.kind) {
            "CAPTURE", "SCAN" -> "CAPTURE_RECORDS"
            "AUDIT" -> "AUDIT_RECORDS"
            "FROZEN" -> "LOCAL_FROZEN_PAYLOAD"
            else -> error("不支持的原机对象")
        }
        return E2aWire.item(kind, row.recordId, objectKind, row.objectId, row.rawBytes,
            row.deviceSeq, row.bytesSha256, row.recordStatus,
            if (scan) record.getString("baleId") else null,
            if (scan) record.getString("expectedDispatchCycle").toLong() else null,
            if (scan) record.getString("captureId") else null)
    }
}
