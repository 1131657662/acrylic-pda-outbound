package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject

/** The original frozen payload remains unchanged; the new recipient description has its own shape. */
internal object TemporaryRecoveredHandoverPayload {
    fun verify(manifest: JSONObject, original: ByteArray, receipts: TemporaryHandoverReceipts.Verified): JSONObject {
        check(receipts.fault && original.isNotEmpty() && original.size <= 32 * 1024 * 1024)
        val body = JSONObject(original.toString(Charsets.UTF_8))
        check(SubmissionJson.canonical(body).toByteArray(Charsets.UTF_8).contentEquals(original)) { "恢复交接须保留原固定正文" }
        val source = receipts.qualification.getJSONObject("source")
        check(JwtVerifier.sha(original) == manifest.getString("recoveryPackageSha256") &&
            manifest.getString("recoveryPackageSha256") == source.getString("payloadSha256"))
        check(body.getInt("schemaVersion") == 2 && body.has("assignmentId") && body.isNull("assignmentId"))
        check(JwtVerifier.sha(SubmissionJson.canonical(body.getJSONObject("temporaryOrder")).toByteArray(Charsets.UTF_8)) == body.getString("planSha256"))
        for (field in listOf("authorizationId", "submissionId", "workRound", "sourceRecoveryEpoch")) check(body.getString(field) == source.getString(field))
        check(body.getJSONObject("source").getString("clientOrderId") == source.getString("clientOrderId") &&
            body.getString("captureHighWatermark") == source.getString("inputHighWatermark"))
        val baseline = receipts.qualification.getJSONObject("baseline")
        for (field in listOf("inventorySnapshotId", "policyVersion")) check(body.getString(field) == baseline.getString(field))
        val proof = receipts.stop.getJSONObject("businessDecision")
        TemporaryHandoverPayload.source(body, proof, receipts); TemporaryHandoverPayload.fault(manifest, body, proof)
        checklist(manifest, body)
        return body
    }
    private fun checklist(manifest: JSONObject, body: JSONObject) {
        val scans = body.getJSONArray("scans"); val lines = manifest.getJSONArray("checklist")
        val active = (0 until scans.length()).map(scans::getJSONObject).filter { it.getString("status") == "ACTIVE" }
        val approved = (0 until lines.length()).map(lines::getJSONObject)
        check(active.size == approved.size && active.map { it.getString("baleId") }.toSet().size == active.size)
        check(approved.map { it.getString("baleId") }.toSet().size == approved.size)
        for (line in approved) {
            TemporaryRecoveredHandoverQualification.fields(line, "baleId deliveryCode dispatchCycle state")
            check(line.getString("state") == "RETAINED") { "恢复交接只支持原批准的全部保留清单" }
            val scan = active.single { it.getString("baleId") == line.getString("baleId") }
            check(scan.getString("deliveryCode") == line.getString("deliveryCode") && scan.getString("expectedDispatchCycle") == line.getString("dispatchCycle"))
        }
    }
}
