package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject

/** Revalidates the immutable signed predecessor; no original scan is copied into a mutable local row. */
internal class TemporaryContinuationBaseline(private val store: TemporaryWorkStore) {
    data class Baseline(val link: TemporaryContinuation, val original: JSONObject, val decision: JSONObject, val rootClientOrderId: String)

    fun read(draft: TemporaryDraft, requireAck: Boolean = true): Baseline? {
        if (draft.workRound == 1L) return null
        val link = checkNotNull(store.db.temporaryContinuations().forTarget(draft.clientOrderId)) { "续作消费记录缺失" }
        check(store.db.temporaryHandoverActivations().caseForTarget(draft.clientOrderId) == null) { "前驱票据类型冲突" }
        val old = checkNotNull(store.db.temporaryDrafts().get(link.sourceClientOrderId))
        check(old.workRound >= 1L && Math.addExact(old.workRound, 1) == draft.workRound) { "续作前驱轮次不连续" }
        val handover = if (store.isHandover(old.clientOrderId)) TemporaryHandoverBaseline(store).read(old, false) else null
        val earlier = if (handover == null) read(old, requireAck = false) else null
        val frozen = checkNotNull(store.db.temporarySubmissions().get(link.sourceSubmissionId))
        check(frozen.clientOrderId == old.clientOrderId && frozen.sha256 == link.sourcePayloadSha256)
        val prepared = checkNotNull(store.db.temporary().byAuthorization(old.authorizationId))
        val request = checkNotNull(store.db.temporary().request(prepared.requestKey))
        val source = TemporaryCatalog.load(store.db, request)
        val verified = TemporaryContinuationProtocol.verify(link.receiptJson.toByteArray(Charsets.UTF_8),
            TemporaryContinuationProtocol.Input(old, frozen, source.manifest,
                TemporaryAccess(store.db, store.connection).snapshot(), JSONObject(request.signingKeyJson),
                handover?.original ?: earlier?.original, earlier?.rootClientOrderId ?: old.clientOrderId))
        check(verified.ticketId == link.ticketId && verified.newClientOrderId == draft.clientOrderId)
        check(verified.toRound == draft.workRound && verified.authorization.getString("authorizationId") == draft.authorizationId)
        val currentPreparation = checkNotNull(store.db.temporary().byAuthorization(draft.authorizationId))
        val currentRequest = checkNotNull(store.db.temporary().request(currentPreparation.requestKey))
        val current = TemporaryCatalog.load(store.db, currentRequest)
        check(current.request.key == link.ticketId && current.request.previousKey == request.key)
        if (requireAck) TemporaryAckVerifier.verify(current.preparation, current.request,
            JSONObject(checkNotNull(current.preparation.ackJson) { "续作下载确认尚未完成" }))
        val original = JSONObject(frozen.payload)
        plan(draft, original)
        sequence(draft, verified.nextOrderScanSeq)
        return Baseline(link, original, JSONObject(link.receiptJson).getJSONObject("businessDecision"),
            earlier?.rootClientOrderId ?: old.clientOrderId)
    }

    private fun sequence(draft: TemporaryDraft, start: Long) {
        val captures = store.db.temporaryScans().captures(draft.clientOrderId)
        captures.forEachIndexed { index, row ->
            check(row.orderScanSeq == Math.addExact(start, index.toLong()) && row.authorizationId == draft.authorizationId &&
                row.originDeviceId == draft.creatorDeviceId && row.operatorId == draft.operatorId) { "续作新输入序号或身份不连续" }
        }
        check(draft.nextScanSeq == Math.addExact(start, captures.size.toLong())) { "续作高水位与耐久输入不一致" }
    }

    private fun plan(draft: TemporaryDraft, original: JSONObject) {
        val expected = JSONObject(original.getJSONObject("temporaryOrder").toString())
            .put("authorizationId", draft.authorizationId).put("clientOrderId", draft.clientOrderId)
        val actual = TemporaryPlanDigest.body(draft, store.db.temporaryDrafts().lines(draft.clientOrderId))
        check(SubmissionJson.canonical(expected) == SubmissionJson.canonical(actual)) { "续作不得修改原固定计划" }
    }
}
