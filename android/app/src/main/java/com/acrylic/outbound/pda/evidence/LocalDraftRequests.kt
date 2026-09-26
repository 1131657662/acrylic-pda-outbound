package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import java.security.MessageDigest

internal class LocalDraftRequests(private val db: PdaDatabase, private val stop: LocalDraftStop) {
    data class Body(val position: Long, val operation: String, val canonical: String)
    fun all(): Sequence<Body> = sequence {
        val signature = checkNotNull(db.localDraftStopSignatures().get(stop.clientOrderId))
        yield(body(0, "OPEN", JSONObject().put("metadata", JSONObject(stop.metadataJson)).put("proof", JSONObject(signature.proofJson))))
        var after = 0L
        while (true) {
            val items = db.localDraftStops().page(stop.clientOrderId, after)
            if (items.isEmpty()) break
            items.forEach { yield(body(it.ordinal, "ITEM", descriptor(stop, it))) }
            after = items.last().ordinal
        }
        check(after == stop.recordCount)
        yield(body(stop.recordCount + 1, "VERIFY", JSONObject().put("evidenceId", stop.evidenceId).put("rootSha256", stop.rootSha256)))
    }
    private fun body(position: Long, operation: String, json: JSONObject): Body {
        val canonical = SubmissionJson.canonical(json)
        check(canonical.toByteArray(Charsets.UTF_8).size in 1..262144)
        return Body(position, operation, canonical)
    }
    companion object {
        fun descriptor(stop: LocalDraftStop, row: LocalDraftStopItem): JSONObject = JSONObject().put("evidenceId", stop.evidenceId)
            .put("ordinal", row.ordinal).put("kind", row.kind).put("recordId", row.recordId).put("deviceSeq", row.deviceSeq)
            .put("recordStatus", row.recordStatus).put("byteCount", row.rawBytes.size).put("bytesSha256", row.bytesSha256)
    }
}

internal class LocalDraftRequestDigest(stop: LocalDraftStop) {
    private val digest = MessageDigest.getInstance("SHA-256").apply {
        update("acrylic-pda-local-draft-requests-v1\n${stop.evidenceId}\n${stop.rootSha256}\n".toByteArray(Charsets.UTF_8))
    }
    var count = 0L
        private set
    fun append(row: LocalDraftRequest) {
        check(row.position == count++)
        E2aItemValidation.uuid(row.originalKey)
        check(E2aWire.sha(row.requestJson.toByteArray(Charsets.UTF_8)) == row.requestSha256)
        val frame = JSONObject().put("position", row.position).put("operation", row.operation)
            .put("originalKey", row.originalKey).put("requestSha256", row.requestSha256)
        digest.update((SubmissionJson.canonical(frame) + "\n").toByteArray(Charsets.UTF_8))
    }
    fun finish(): String = digest.digest().joinToString("") { "%02x".format(it) }
}
