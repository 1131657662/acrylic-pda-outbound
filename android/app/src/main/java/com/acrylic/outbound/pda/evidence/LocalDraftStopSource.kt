package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.temporary.TemporaryDraft
import com.acrylic.outbound.pda.temporary.TemporaryPlanDigest
import com.acrylic.outbound.pda.temporary.TemporaryWorkStore
import org.json.JSONObject

internal object LocalDraftStopSource {
    fun draft(store: TemporaryWorkStore, draft: TemporaryDraft): JSONObject =
        TemporaryPlanDigest.body(draft, store.db.temporaryDrafts().lines(draft.clientOrderId))
            .put("warehouseId", draft.warehouseId).put("operatorId", draft.operatorId)
            .put("temporaryNo", draft.temporaryNo).put("createdAt", draft.createdAt)
            .put("state", draft.state).put("nextScanSeq", draft.nextScanSeq.toString())
            .put("planSha256", draft.planSha256)

    fun metadata(store: TemporaryWorkStore, draft: TemporaryDraft, evidenceId: String,
        eventId: String, boundary: E2aDeviceCoverage.Boundary): String {
        val source = store.source(draft.clientOrderId)
        val request = source.request
        return SubmissionJson.canonical(JSONObject().put("schemaVersion", 1)
            .put("kind", "LOCAL_DRAFT_STOP").put("evidenceId", evidenceId)
            .put("draft", draft(store, draft)).put("stopEventId", eventId)
            .put("captureHighWatermark", boundary.captureHighWatermark.toString())
            .put("auditThroughSeq", boundary.auditThroughSeq.toString())
            .put("sourceRecoveryEpoch", request.recoveryEpoch)
            .put("snapshotId", source.manifest.getString("inventorySnapshotId"))
            .put("preparationRequestKey", request.key).put("preparationRequest", request.bodyJson)
            .put("preparationReceipt", checkNotNull(request.receiptJson))
            .put("preparationSigningKey", request.signingKeyJson)
            .put("manifestSha256", source.preparation.manifestSha256)
            .put("localDeletionAllowed", false))
    }
}
