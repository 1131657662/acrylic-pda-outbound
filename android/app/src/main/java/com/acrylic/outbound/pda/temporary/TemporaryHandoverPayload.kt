package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject

internal object TemporaryHandoverPayload {
    fun verify(manifest: JSONObject, original: ByteArray, receipts: TemporaryHandoverReceipts.Verified): JSONObject {
        check(original.isNotEmpty() && original.size <= 32 * 1024 * 1024)
        val body = JSONObject(original.toString(Charsets.UTF_8))
        check(SubmissionJson.canonical(body).toByteArray(Charsets.UTF_8).contentEquals(original)) { "临时交接不是原固定正文" }
        val sha = JwtVerifier.sha(original)
        check(sha == manifest.getString("recoveryPackageSha256") && sha == receipts.qualification.getString("payloadSha256"))
        check(body.has("assignmentId") && body.isNull("assignmentId") && body.getInt("schemaVersion") == 2)
        val planSha = JwtVerifier.sha(SubmissionJson.canonical(body.getJSONObject("temporaryOrder")).toByteArray(Charsets.UTF_8))
        check(planSha == body.getString("planSha256") && planSha == receipts.qualification.getString("planSha256"))
        source(body, receipts.stop)
        watermarks(manifest, body, receipts.stop)
        return body
    }

    private fun source(body: JSONObject, stop: JSONObject) {
        val source = stop.getJSONObject("source"); val context = stop.getJSONObject("caseContext")
        check(source.getString("sourceKind") == "TEMP" && source.has("assignmentId") && source.isNull("assignmentId"))
        for (field in listOf("scopeId", "submissionId", "warehouseId")) check(source.getString(field) == context.getString(field))
        check(source.getString("orderId") == context.getString("canonicalOrderId") &&
            source.getString("workRound") == context.getString("fromWorkRound"))
        for (field in listOf("authorizationId", "submissionId", "sourceRecoveryEpoch", "workRound"))
            check(source.getString(field) == body.getString(field))
        check(source.getString("deviceId") == body.getString("freezeDeviceId") &&
            source.getString("deviceId") == stop.getJSONObject("actor").getString("deviceId") &&
            source.getString("operatorId") == stop.getJSONObject("actor").getString("principalId"))
        val origin = body.getJSONObject("source")
        check(origin.getString("kind") == "TEMP")
        for (field in listOf("clientOrderId", "creatorDeviceId"))
            check(source.getString(field) == origin.getString(field) && origin.getString(field) == body.getJSONObject("temporaryOrder").getString(field))
    }

    private fun watermarks(manifest: JSONObject, body: JSONObject, stop: JSONObject) {
        val original = stop.getJSONObject("originalCommand")
        check(original.getString("caseId") == manifest.getString("caseId"))
        val command = original.getJSONObject("command"); val proof = stop.getJSONObject("temporaryStop")
        check(command.getString("sourceKind") == "TEMP" && proof.getString("sourceKind") == "TEMP" &&
            proof.getString("status") == "VERIFIED_COMPLETE_AT_STOP" && !proof.getBoolean("mayResumeScanning") && !proof.getBoolean("stockReleased"))
        for (field in listOf("sourceEvidenceSetId", "verificationId", "setSha256", "captureHighWatermark", "auditThroughSeq"))
            check(command.getString(field) == proof.getString(field))
        for (field in listOf("authorizationId", "submissionId", "workRound")) check(proof.getString(field) == body.getString(field))
        val high = manifest.getString("inputHighWatermark").toLong()
        check(high >= 0 && high.toString() == manifest.getString("inputHighWatermark"))
        check(proof.getString("orderScanHighWatermark") == high.toString())
        check(command.optString("orderScanHighWatermark", high.toString()) == high.toString())
        val captures = body.getJSONArray("captures")
        val records = (0 until captures.length()).map { captures.getJSONObject(it) }
        records.forEachIndexed { index, row ->
            check(row.getString("orderScanSeq") == (index + 1L).toString()) { "交接累计输入序号不连续" }
        }
        val sourceHigh = captures.length().toLong()
        val ownHigh = records.filter { it.getString("originDeviceId") == body.getString("freezeDeviceId") }
            .maxOfOrNull { TemporaryVerifier.positive(it.getString("deviceCaptureSeq")) } ?: 0L
        val deviceHigh = proof.getString("captureHighWatermark").toLong()
        check(sourceHigh == high && high.toString() == body.getString("captureHighWatermark") && deviceHigh >= ownHigh) {
            "交接来源与设备高水位不一致"
        }
    }
}
