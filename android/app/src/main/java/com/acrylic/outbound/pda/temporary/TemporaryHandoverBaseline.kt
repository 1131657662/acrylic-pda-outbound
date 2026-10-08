package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject

/** Revalidates the durable receiving installation before reading or adding any new facts. */
internal class TemporaryHandoverBaseline(private val store: TemporaryWorkStore) {
    data class Baseline(val activation: TemporaryHandoverActivation, val stage: TemporaryHandoverStage,
        val original: JSONObject, val retained: List<JSONObject>, val decision: JSONObject)

    fun read(draft: TemporaryDraft, requireAck: Boolean = draft.state != "HANDOVER_BLOCKED"): Baseline {
        val db = store.db; val caseId = checkNotNull(db.temporaryHandoverActivations().caseForTarget(draft.clientOrderId))
        val activation = checkNotNull(db.temporaryHandoverActivations().get(caseId))
        check(db.temporaryContinuations().forTarget(draft.clientOrderId) == null &&
            db.temporaryContinuations().get(activation.ticketId) == null && db.preparations().order(draft.clientOrderId) == null) {
            "临时交接不得复用正式任务或续作票据"
        }
        check(JwtVerifier.sha(activation.receiptBytes) == activation.receiptSha256)
        val stage = TemporaryHandoverRepository(db, store.connection).read(caseId)
        val confirmation = TemporaryHandoverConfirmationRepository(db, store.connection).read(caseId)
        check(confirmation?.state == "CONFIRMED")
        val expected = TemporaryAccess(db, store.connection).snapshot().put("signingKey", JSONObject(activation.signingKeyJson))
        val verified = TemporaryHandoverActivationProtocol.verify(activation.receiptBytes,
            TemporaryHandoverActivationProtocol.Input(stage, checkNotNull(db.temporaryHandoverConfirmations().receipt(caseId)), expected))
        check(draft.authorizationId == activation.authorizationId && draft.workRound == stage.toWorkRound &&
            draft.creatorDeviceId == stage.targetDeviceId && draft.operatorId == stage.targetUserId && draft.warehouseId == stage.warehouseId)
        check(verified.decision.getString("ticketId") == activation.ticketId && activation.scopeId == stage.scopeId)
        val original = JSONObject(stage.payloadBytes.toString(Charsets.UTF_8))
        plan(draft, original); sequence(draft, Math.addExact(stage.inputHighWatermark, 1)); qualification(activation, requireAck)
        val retained = TemporaryHandoverRetained.scans(stage)
        val guards = db.temporaryHandoverGuards().forCase(caseId)
        check(guards == retained.map { TemporaryHandoverRetained.guard(stage, it) }.sortedBy { it.barcode }) { "交接保留条码占用不完整" }
        opening(draft, stage.caseId)
        return Baseline(activation, stage, original, retained, verified.decision)
    }

    private fun opening(draft: TemporaryDraft, caseId: String) {
        val events = store.db.temporaryScans().events(draft.clientOrderId).filter { it.type == "TEMP_HANDOVER_WORK_STARTED" }
        if (draft.state == "HANDOVER_BLOCKED") {
            check(events.isEmpty() && store.db.temporaryScans().captures(draft.clientOrderId).isEmpty())
            return
        }
        val event = events.single()
        check(event.detail == caseId && event.originDeviceId == draft.creatorDeviceId && event.operatorId == draft.operatorId &&
            event.captureId == null) { "交接开轮审计与作业不一致" }
    }

    private fun qualification(row: TemporaryHandoverActivation, requireAck: Boolean) {
        val request = checkNotNull(store.db.temporary().request(row.ticketId))
        check(SubmissionJson.canonical(JSONObject(request.bodyJson).getJSONObject("temporaryHandoverActivation")) ==
            SubmissionJson.canonical(JSONObject(row.receiptBytes.toString(Charsets.UTF_8))))
        val catalog = TemporaryCatalog.load(store.db, request)
        check(catalog.preparation.authorizationId == row.authorizationId && request.previousKey == null)
        if (requireAck) TemporaryAckVerifier.verify(catalog.preparation, request,
            JSONObject(checkNotNull(catalog.preparation.ackJson) { "交接新资格资料尚未确认下载" }))
    }

    private fun sequence(draft: TemporaryDraft, start: Long) {
        val captures = store.db.temporaryScans().captures(draft.clientOrderId)
        captures.forEachIndexed { index, row ->
            check(row.orderScanSeq == Math.addExact(start, index.toLong()) && row.originDeviceId == draft.creatorDeviceId &&
                row.operatorId == draft.operatorId && row.authorizationId == draft.authorizationId) { "交接新输入来源或序号不连续" }
        }
        check(draft.nextScanSeq == Math.addExact(start, captures.size.toLong()))
    }

    private fun plan(draft: TemporaryDraft, original: JSONObject) {
        val expected = JSONObject(original.getJSONObject("temporaryOrder").toString()).put("clientOrderId", draft.clientOrderId)
            .put("creatorDeviceId", draft.creatorDeviceId).put("authorizationId", draft.authorizationId)
        check(SubmissionJson.canonical(expected) == SubmissionJson.canonical(
            TemporaryPlanDigest.body(draft, store.db.temporaryDrafts().lines(draft.clientOrderId)))) { "交接不得修改原固定计划" }
    }
}
