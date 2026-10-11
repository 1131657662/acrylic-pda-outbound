package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.temporary.*
import org.json.JSONObject

/** Verifies previously consumed R06 links from immutable bytes, without granting a new live session. */
internal class RescueTemporaryPrefix(private val db: PdaDatabase, private val rows: RescueRows) {
    data class Prefix(val original: JSONObject, val rootId: String, val evidence: List<JSONObject>)
    fun read(draft: TemporaryDraft, depth: Int = 0): Prefix? {
        if (draft.workRound == 1L) return null
        check(depth < 32) { "原续作链超过救援读取上限，请保留原库" }
        val link = checkNotNull(db.temporaryContinuations().forTarget(draft.clientOrderId)) { "原续作前驱缺失，请保留原资料" }
        val old = checkNotNull(db.temporaryDrafts().get(link.sourceClientOrderId))
        check(old.workRound + 1 == draft.workRound && old.creatorDeviceId == draft.creatorDeviceId &&
            old.operatorId == draft.operatorId && old.warehouseId == draft.warehouseId)
        rows.originalBytes("SELECT length(CAST(payload AS BLOB)) FROM temporary_submission WHERE id=?", link.sourceSubmissionId)
        rows.reserve(link.receiptJson)
        val frozen = checkNotNull(db.temporarySubmissions().get(link.sourceSubmissionId))
        check(frozen.sha256 == link.sourcePayloadSha256 && RescueBundle.sha(frozen.payload.toByteArray(Charsets.UTF_8)) == frozen.sha256)
        val previous = read(old, depth + 1)
        verify(old, draft, frozen, link, previous)
        val evidence = previous?.evidence.orEmpty() + listOf(proof(link), RescueOriginals.publicSource("PREDECESSOR_SUBMISSION", frozen.payload))
        return Prefix(JSONObject(frozen.payload), previous?.rootId ?: old.clientOrderId, evidence)
    }
    private fun verify(old: TemporaryDraft, draft: TemporaryDraft, frozen: TemporarySubmission,
        link: TemporaryContinuation, previous: Prefix?) {
        val preparation = checkNotNull(db.temporary().byAuthorization(old.authorizationId))
        val request = checkNotNull(db.temporary().request(preparation.requestKey))
        val key = JSONObject(request.signingKeyJson)
        val recordedIdentity = recordedIdentity(link.receiptJson, key)
        val verified = TemporaryContinuationProtocol.verify(link.receiptJson.toByteArray(Charsets.UTF_8),
            TemporaryContinuationProtocol.Input(old, frozen, TemporaryAckVerifier.declaration(preparation, request),
                recordedIdentity, key, previous?.original, previous?.rootId ?: old.clientOrderId))
        check(verified.ticketId == link.ticketId && verified.newClientOrderId == draft.clientOrderId &&
            verified.toRound == draft.workRound && verified.authorization.getString("authorizationId") == draft.authorizationId)
    }
    private fun proof(link: TemporaryContinuation): JSONObject =
        JSONObject().put("type", "CONTINUATION_LINK_IDENTIFIERS").put("ticketId", link.ticketId)
            .put("sourceClientOrderId", link.sourceClientOrderId).put("sourceSubmissionId", link.sourceSubmissionId)
            .put("sourcePayloadSha256", link.sourcePayloadSha256).put("newClientOrderId", link.newClientOrderId)
            .put("originalReceiptSha256", RescueBundle.sha(link.receiptJson.toByteArray(Charsets.UTF_8)))
    private fun recordedIdentity(text: String, key: JSONObject): JSONObject {
        val receipt = JSONObject(text)
        val claims = JwtVerifier.verify(receipt.getString("signedReceipt"), key, "acrylic-r06-command-receipt-v1", 262144)
        val context = claims.getJSONObject("receipt").getJSONObject("originalContext")
        val actor = claims.getJSONObject("receipt").getJSONObject("actor")
        return JSONObject().put("userId", actor.getString("principalId")).put("deviceId", actor.getString("deviceId"))
            .put("warehouseId", context.getString("warehouseId")).put("userAuthVersion", actor.getString("userAuthVersion"))
            .put("deviceVersion", actor.getString("deviceVersion")).put("serverRecoveryEpoch", context.getString("sourceRecoveryEpoch"))
    }
    fun combine(prefix: Prefix?, snapshot: RescueSnapshot): RescueSnapshot {
        if (prefix == null) return snapshot
        fun original(field: String): List<JSONObject> {
            val rows = prefix.original.getJSONArray(field)
            return (0 until rows.length()).map { JSONObject(rows.getJSONObject(it).toString()) }
        }
        val evidence = snapshot.originals.getValue("source-evidence.ndjson").toString(Charsets.UTF_8)
            .lineSequence().filter { it.isNotBlank() }.map(::JSONObject).toList() + prefix.evidence
        return snapshot.copy(captures = original("captures") + snapshot.captures, scans = original("scans") + snapshot.scans,
            events = original("events").onEach { it.put("orderPosition", it.getString("localSeq")) } + snapshot.events,
            originals = snapshot.originals + ("source-evidence.ndjson" to RescueText.jsonLines(evidence)))
    }
}
