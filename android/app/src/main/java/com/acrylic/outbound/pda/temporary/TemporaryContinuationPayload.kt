package com.acrylic.outbound.pda.temporary

import org.json.JSONArray
import org.json.JSONObject

/** Produces the cumulative wire facts from immutable original bytes plus separately owned new facts. */
internal class TemporaryContinuationPayload(private val store: TemporaryWorkStore) {
    fun cumulative(draft: TemporaryDraft, body: JSONObject): JSONObject {
        val baseline = TemporaryContinuationBaseline(store).read(draft) ?: return body
        val original = baseline.original
        for (field in listOf("captures", "scans", "events")) {
            val combined = JSONArray(original.getJSONArray(field).toString())
            val current = body.getJSONArray(field)
            for (i in 0 until current.length()) combined.put(current.getJSONObject(i))
            body.put(field, combined)
        }
        val summary = body.getJSONObject("captureSummary")
        val old = original.getJSONObject("captureSummary")
        summary.keys().asSequence().toList().forEach { field ->
            summary.put(field, Math.addExact(summary.getString(field).toLong(), old.getString(field).toLong()).toString())
        }
        return body.put("temporaryContinuation", JSONObject().put("version", 1)
            .put("ticketId", baseline.link.ticketId).put("predecessorSubmissionId", baseline.link.sourceSubmissionId)
            .put("predecessorPayloadSha256", baseline.link.sourcePayloadSha256)
            .put("retainedManifestSha256", baseline.decision.getString("retainedManifestSha256")))
    }
}
