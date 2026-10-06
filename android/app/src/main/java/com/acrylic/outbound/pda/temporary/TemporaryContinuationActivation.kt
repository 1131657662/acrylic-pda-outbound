package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.evidence.LocalE2aStopRepository
import com.acrylic.outbound.pda.update.UpdateGate
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Callable

class TemporaryContinuationActivation(private val store: TemporaryWorkStore) {
    fun activate(id: String, original: ByteArray): TemporaryDraft = UpdateGate.business {
        val raw = original.copyOf(); val access = TemporaryAccess(store.db, store.connection); val expected = access.snapshot()
        store.db.runInTransaction(Callable {
            val draft = store.load(id); store.check(id, false)
            val source = store.source(id)
            val frozen = checkNotNull(store.db.temporarySubmissions().forOrder(id)) { "原冻结正文不存在" }
            LocalE2aStopRepository(store).record(id)
            val input = TemporaryContinuationProtocol.Input(draft, frozen, source.manifest, expected, JSONObject(source.request.signingKeyJson))
            val verified = TemporaryContinuationProtocol.verify(raw, input)
            val saved = store.db.temporaryContinuations().forSource(id)
            val result = if (saved != null) replay(saved, verified, frozen) else persist(draft, verified, frozen)
            access.check(expected); result
        })
    }

    private fun persist(draft: TemporaryDraft, verified: TemporaryContinuationProtocol.Verified, frozen: TemporarySubmission): TemporaryDraft {
        val source = store.source(draft.clientOrderId)
        val manifest = JSONObject(verified.authorization.getString("manifest"))
        TemporaryVerifier.time(manifest, store.connection.now())
        val newRequest = source.request.copy(key = verified.ticketId, receiptJson = verified.authorization.toString(), previousKey = null)
        store.db.temporary().insertRequest(newRequest)
        val payloads = TemporaryParts(store.db).read(draft.authorizationId, source.manifest)
        TemporaryActivation(store.db, store.connection).activate(TemporaryActivation.Input(newRequest,
            TemporaryAccess(store.db, store.connection).snapshot(), payloads))
        val history = JSONArray(draft.authorizationHistoryJson).put(JSONObject().put("authorizationId", draft.authorizationId)
            .put("clientOrderId", draft.clientOrderId).put("submissionId", frozen.id).put("payloadSha256", frozen.sha256)
            .put("ticketId", verified.ticketId).put("workRound", draft.workRound.toString()))
        val next = draft.copy(clientOrderId = verified.newClientOrderId,
            authorizationId = verified.authorization.getString("authorizationId"), workRound = verified.toRound,
            state = "CONTINUATION_BLOCKED", nextScanSeq = verified.nextOrderScanSeq, authorizationHistoryJson = SubmissionJson.canonical(history))
        val lines = store.db.temporaryDrafts().lines(draft.clientOrderId).map { it.copy(clientOrderId = next.clientOrderId) }
        val saved = next.copy(planSha256 = TemporaryPlanDigest.digest(next, lines))
        store.db.temporaryDrafts().insert(saved); store.db.temporaryDrafts().insertLines(lines)
        store.db.temporaryContinuations().insert(TemporaryContinuation(verified.ticketId, draft.clientOrderId,
            frozen.id, frozen.sha256, saved.clientOrderId, verified.receiptJson))
        store.event(saved, null, "TEMP_CONTINUATION_ACTIVATED", verified.ticketId)
        return saved
    }

    private fun replay(saved: TemporaryContinuation, verified: TemporaryContinuationProtocol.Verified, frozen: TemporarySubmission): TemporaryDraft {
        check(saved.ticketId == verified.ticketId && saved.receiptJson == verified.receiptJson &&
            saved.sourceSubmissionId == frozen.id && saved.sourcePayloadSha256 == frozen.sha256) { "续作回执与已保存消费记录冲突" }
        val next = store.load(saved.newClientOrderId); store.check(next.clientOrderId, false)
        check(next.authorizationId == verified.authorization.get("authorizationId") && next.workRound == verified.toRound)
        check(next.clientOrderId == verified.newClientOrderId)
        return next
    }
}
