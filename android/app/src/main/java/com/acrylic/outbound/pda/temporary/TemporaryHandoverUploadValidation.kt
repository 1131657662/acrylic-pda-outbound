package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject

internal object TemporaryHandoverUploadValidation {
    fun verify(db: PdaDatabase, connection: TemporaryConnection, local: TemporarySubmission, body: JSONObject) {
        check(!body.has("temporaryContinuation") && body.isNull("assignmentId") && body.isNull("continuationId"))
        val store = TemporaryWorkStore(db, connection) { error("Upload validation cannot create events") }
        val draft = store.load(local.clientOrderId); store.check(draft.clientOrderId, false)
        val baseline = TemporaryHandoverBaseline(store).read(draft)
        check(body.getString("workRound") == draft.workRound.toString())
        check(SubmissionJson.canonical(body.getJSONObject("temporaryHandover")) ==
            SubmissionJson.canonical(TemporaryHandoverPayloadBuilder(store).reference(baseline))) { "交接冻结前驱引用不一致" }
        for (field in listOf("captures", "scans", "events")) {
            val before = baseline.original.getJSONArray(field); val current = body.getJSONArray(field)
            check(current.length() >= before.length())
            for (i in 0 until before.length()) check(SubmissionJson.canonical(before.getJSONObject(i)) ==
                SubmissionJson.canonical(current.getJSONObject(i))) { "交接冻结原文前缀已变化" }
        }
    }
}
