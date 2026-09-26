package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.temporary.TemporaryDraft
import com.acrylic.outbound.pda.temporary.TemporaryWorkStore
import org.json.JSONObject

internal class LocalDraftStopIntegrity(private val store: TemporaryWorkStore) {
    fun verify(stop: LocalDraftStop, draft: TemporaryDraft) {
        check(store.db.inTransaction() && draft.state == "EVIDENCE_STOPPED" && stop.clientOrderId == draft.clientOrderId)
        check(store.db.temporarySubmissions().forOrder(draft.clientOrderId) == null)
        val metadata = JSONObject(stop.metadataJson)
        val original = metadata.getJSONObject("draft").getString("state")
        check(original in setOf("DRAFT", "SCANNING"))
        val boundary = E2aDeviceCoverage.Boundary(metadata.getString("captureHighWatermark").toLong(),
            metadata.getString("auditThroughSeq").toLong())
        check(LocalDraftStopSource.metadata(store, draft.copy(state = original), stop.evidenceId,
            metadata.getString("stopEventId"), boundary) == stop.metadataJson)
        val current = E2aDeviceCoverage(store.db).verify(draft.creatorDeviceId)
        check(current.captureHighWatermark >= boundary.captureHighWatermark && current.auditThroughSeq >= boundary.auditThroughSeq)
        event(stop, draft, metadata)
        records(stop, draft, boundary)
    }

    private fun event(stop: LocalDraftStop, draft: TemporaryDraft, metadata: JSONObject) {
        E2aItemValidation.uuid(stop.evidenceId)
        val events = store.db.temporaryScans().events(draft.clientOrderId)
        val row = events.single { it.type == "LOCAL_EVIDENCE_STOP" }
        check(row.id == metadata.getString("stopEventId") && row.captureId == null && row.detail == stop.evidenceId &&
            row.originDeviceId == draft.creatorDeviceId && row.operatorId == draft.operatorId &&
            row.localSeq.toString() == metadata.getString("auditThroughSeq"))
        check(events.last() == row)
    }

    private fun records(stop: LocalDraftStop, draft: TemporaryDraft, boundary: E2aDeviceCoverage.Boundary) {
        val digest = LocalDraftStopDigest(stop.metadataJson)
        val originals = LocalDraftStopOriginals(store, draft)
        var captureSeq = 0L; var auditSeq = 0L
        val keys = hashSetOf<String>()
        while (true) {
            val page = store.db.localDraftStops().page(stop.clientOrderId, digest.count)
            if (page.isEmpty()) break
            for (row in page) {
                check(row.clientOrderId == stop.clientOrderId && keys.add("${row.kind}/${row.recordId}"))
                digest.append(row)
                originals.compare(row)
                if (row.kind == "CAPTURE") check(row.deviceSeq == ++captureSeq)
                if (row.kind == "SCAN") check(row.deviceSeq <= boundary.auditThroughSeq)
                if (row.kind == "AUDIT") check(row.deviceSeq == ++auditSeq)
            }
        }
        originals.complete()
        check(captureSeq == boundary.captureHighWatermark && auditSeq == boundary.auditThroughSeq)
        check(digest.count == stop.recordCount && digest.bytes == stop.totalBytes && digest.finish() == stop.rootSha256)
    }
}
