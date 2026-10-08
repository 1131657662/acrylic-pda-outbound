package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject

internal object TemporaryContinuationUploadValidation {
    fun verify(db: PdaDatabase, connection: TemporaryConnection, local: TemporarySubmission, body: JSONObject) {
        if (body.getString("workRound") == "1") return
        if (body.has("temporaryHandover")) { TemporaryHandoverUploadValidation.verify(db, connection, local, body); return }
        val store = TemporaryWorkStore(db, connection) { error("Upload verification cannot create local events") }
        val draft = store.load(local.clientOrderId); store.check(draft.clientOrderId, false)
        check(body.getString("workRound") == draft.workRound.toString())
        val baseline = checkNotNull(TemporaryContinuationBaseline(store).read(draft))
        val reference = body.getJSONObject("temporaryContinuation")
        check(reference.getString("ticketId") == baseline.link.ticketId &&
            reference.getString("predecessorSubmissionId") == baseline.link.sourceSubmissionId &&
            reference.getString("predecessorPayloadSha256") == baseline.link.sourcePayloadSha256 &&
            reference.getString("retainedManifestSha256") == baseline.decision.getString("retainedManifestSha256")) { "续作冻结前驱引用不一致" }
        for (field in listOf("captures", "scans", "events")) {
            val before = baseline.original.getJSONArray(field); val next = body.getJSONArray(field)
            check(next.length() >= before.length())
            for (i in 0 until before.length()) check(SubmissionJson.canonical(before.getJSONObject(i)) ==
                SubmissionJson.canonical(next.getJSONObject(i))) { "续作冻结原文前缀已变化" }
        }
    }
}
