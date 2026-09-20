package com.acrylic.outbound.pda.retention

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.*
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset

object SubmissionArchiveVerifier {
    fun request(local: LocalSubmission, receiptKey: JSONObject): JSONObject {
        check(local.state == "SYNCED" && JwtVerifier.sha(local.payload.toByteArray(Charsets.UTF_8)) == local.sha256) { "原批次或正文尚不可归档确认" }
        val receipt = JSONObject(checkNotNull(local.receipt))
        check(ReceiptVerifier.verify(local, receipt, receiptKey) == "SYNCED") { "原成功回执尚未核验" }
        return JSONObject().put("payloadSha256", local.sha256).put("receiptSha256", digest(receipt))
    }
    fun verify(local: LocalSubmission, auth: JSONObject, request: JSONObject, response: JSONObject, key: JSONObject) {
        val signed = JwtVerifier.verify(response.getString("signedArchive"), key, "acrylic-pda-archive").getJSONObject("archive")
        val unsigned = JSONObject(response.toString()).apply { remove("signedArchive") }
        check(SubmissionJson.canonical(signed) == SubmissionJson.canonical(unsigned)) { "归档签名与响应不一致" }
        val body = JSONObject(local.payload)
        val expected = JSONObject(request.toString()).put("submissionId", local.id).put("assignmentId", local.assignmentId)
            .put("orderId", body.getJSONObject("source").getString("sourceOrderId"))
            .put("warehouseId", auth.getString("warehouseId")).put("operatorId", auth.getString("userId"))
            .put("freezeDeviceId", auth.getString("deviceId")).put("workRound", body.getString("workRound"))
            .put("sourceRecoveryEpoch", body.getString("sourceRecoveryEpoch")).put("recoveryEpoch", auth.getString("serverRecoveryEpoch"))
            .put("payloadBytes", local.payload.toByteArray(Charsets.UTF_8).size.toString())
            .put("receiptBytes", SubmissionJson.canonical(JSONObject(checkNotNull(local.receipt))).toByteArray(Charsets.UTF_8).size.toString())
            .put("completedAt", JSONObject(local.receipt).getString("completedAt"))
            .put("archiveVersion", 1).put("storagePolicy", "SERVER_DURABLE_FILES_V1").put("localDeletionAllowed", false)
        expected.keys().forEach { check(response.get(it) == expected.get(it)) { "归档确认与原批次不一致：$it" } }
        val archived = Instant.parse(response.getString("archivedAt"))
        check(!archived.isBefore(Instant.parse(expected.getString("completedAt"))) &&
            !Instant.parse(response.getString("retainUntil")).isBefore(archived.atOffset(ZoneOffset.UTC).plusYears(5).toInstant())) { "归档留存期限无效" }
    }
    private fun digest(value: JSONObject) = JwtVerifier.sha(SubmissionJson.canonical(value).toByteArray(Charsets.UTF_8))
}
