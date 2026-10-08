package com.acrylic.outbound.pda.temporary

import org.json.JSONArray
import org.json.JSONObject

/** Keeps every original fact as a fixed prefix, with a separate TEMP handover reference. */
internal class TemporaryHandoverPayloadBuilder(private val store: TemporaryWorkStore) {
    fun cumulative(draft: TemporaryDraft, body: JSONObject): JSONObject {
        val baseline = TemporaryHandoverBaseline(store).read(draft)
        for (field in listOf("captures", "scans", "events")) {
            val combined = JSONArray(baseline.original.getJSONArray(field).toString())
            val current = body.getJSONArray(field)
            for (i in 0 until current.length()) combined.put(current.getJSONObject(i))
            body.put(field, combined)
        }
        val summary = body.getJSONObject("captureSummary")
        val old = baseline.original.getJSONObject("captureSummary")
        summary.keys().asSequence().toList().forEach { field ->
            summary.put(field, Math.addExact(summary.getString(field).toLong(), old.getString(field).toLong()).toString())
        }
        return body.put("temporaryHandover", reference(baseline))
    }

    internal fun reference(baseline: TemporaryHandoverBaseline.Baseline) = JSONObject().put("version", 1)
        .put("ticketId", baseline.activation.ticketId).put("caseId", baseline.stage.caseId)
        .put("predecessorSubmissionId", baseline.stage.submissionId).put("predecessorPayloadSha256", baseline.stage.payloadSha256)
        .put("sourcePrefixSha256", baseline.decision.getString("sourcePrefixSha256"))
}
