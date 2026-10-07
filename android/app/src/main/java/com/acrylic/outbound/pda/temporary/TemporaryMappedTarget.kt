package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.network.SessionIdentity
import org.json.JSONObject

/** A changed result target requires a fresh device-scoped controlled mapping for this exact submission. */
internal class TemporaryMappedTarget(private val db: PdaDatabase, private val connection: TemporaryConnection) {
    fun refresh(local: TemporarySubmission, reply: JSONObject, expected: JSONObject): String? {
        val previous = local.receipt?.let(::JSONObject) ?: return null
        if (previous.isNull("orderId") || previous.optString("orderId") == reply.optString("orderId")) return null
        val target = reply.getString("orderId"); TemporaryVerifier.uuid(target)
        val store = TemporaryWorkStore(db, connection) { error("Mapping verification cannot create events") }
        val draft = store.load(local.clientOrderId); store.check(draft.clientOrderId, false)
        val baseline = TemporaryContinuationBaseline(store).read(draft)
        val root = baseline?.rootClientOrderId ?: draft.clientOrderId
        val scope = baseline?.decision?.getString("scopeId")
        var matched = 0
        SourceBindingRepository(db, { SessionIdentity.requireSame(expected, connection.current()); checkNotNull(connection.current()) },
            { path, epoch, auth ->
                val page = connection.call(TemporaryConnection.Call(path, auth, epoch = epoch))
                val items = page.getJSONArray("items")
                for (i in 0 until items.length()) {
                    val row = items.getJSONObject(i)
                    if (row.optString("clientOrderId") != root) continue
                    requireMapping(row, local, reply, scope)
                    matched++
                }
                page
            }).installSource(root)
        check(matched == 1) { "正式目标已变化，缺少本轮人工关联记录，请保留原件后重试" }
        return target
    }

    private fun requireMapping(row: JSONObject, local: TemporarySubmission, reply: JSONObject, scope: String?) {
        val body = JSONObject(local.payload)
        val statuses = if (reply.getString("status") == "REVIEW_REQUIRED") setOf("REVIEW_REQUIRED", "SUCCEEDED")
            else setOf(reply.getString("status"))
        check(row.get("installRequired") == true && row.getString("mappingOrigin") == "CONTROLLED_LINK" &&
            row.getString("currentSubmissionId") == local.id && row.getString("workRound") == body.getString("workRound") &&
            row.getString("submissionStatus") in statuses &&
            row.getString("canonicalOrderId") == reply.getString("orderId") &&
            (scope == null || row.getString("scopeId") == scope)) { "人工关联记录与原来源、本轮或目标不一致" }
        TemporaryVerifier.uuid(row.getString("scopeId")); TemporaryVerifier.uuid(row.getString("mappingId"))
        val version = TemporaryVerifier.positive(row.getString("mappingVersion")); check(version > 1)
        db.sourceBindings().get(row.getString("scopeId"))?.let { prior ->
            check(version >= prior.mappingVersion.toLong() && (version != prior.mappingVersion.toLong() ||
                prior.canonicalOrderId == row.getString("canonicalOrderId"))) { "人工关联版本倒退或同版本目标冲突" }
        }
    }
}
