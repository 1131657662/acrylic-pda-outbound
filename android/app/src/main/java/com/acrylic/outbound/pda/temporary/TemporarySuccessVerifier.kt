package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import java.time.Instant
import java.util.Base64

/** Historical TEMP success. This never grants a product action or local deletion. */
object TemporarySuccessVerifier {
    fun verify(local: TemporarySubmission, original: ByteArray, trustedKey: JSONObject): JSONObject {
        val envelope = TemporarySuccessJson.parse(original)
        check(envelope.get("code") == "OK") { "服务器未返回成功响应" }
        val receipt = envelope.getJSONObject("data")
        signature(receipt, trustedKey)
        val body = JSONObject(local.payload)
        check(SubmissionJson.canonical(body) == local.payload &&
            JwtVerifier.sha(local.payload.toByteArray(Charsets.UTF_8)) == local.sha256) { "原冻结正文已变化" }
        check(body.get("submissionId") == local.id)
        val round = TemporaryVerifier.positive(body.getString("workRound"))
        if (round > 1) {
            val reference = body.getJSONObject("temporaryContinuation")
            check(reference.keys().asSequence().toSet() == setOf("version", "ticketId", "predecessorSubmissionId",
                "predecessorPayloadSha256", "retainedManifestSha256") && reference.getInt("version") == 1)
            TemporaryVerifier.uuid(reference.getString("ticketId")); TemporaryVerifier.uuid(reference.getString("predecessorSubmissionId"))
            for (field in listOf("predecessorPayloadSha256", "retainedManifestSha256"))
                check(reference.getString(field).matches(Regex("[0-9a-f]{64}")))
        }
        check(local.state != "ABORTED" && local.state != "REJECTED") { "本地已存冲突终态，须保留原件" }
        val source = body.getJSONObject("source")
        check(source.keys().asSequence().toSet() == setOf("kind", "creatorDeviceId", "clientOrderId"))
        check(source.get("kind") == "TEMP" && source.get("clientOrderId") == local.clientOrderId)
        check(source.get("creatorDeviceId") == body.get("freezeDeviceId"))
        check(body.isNull("assignmentId") && body.isNull("continuationId") && body.getJSONArray("dispositions").length() == 0)
        context(local, body, receipt)
        TemporarySuccessProducts.verify(body, receipt)
        return receipt
    }

    private fun signature(receipt: JSONObject, key: JSONObject) {
        val token = receipt.get("signedReceipt") as String; val parts = token.split('.')
        check(parts.size == 3 && token.length <= 262144)
        val decoded = parts.map { part ->
            check(part.matches(Regex("[A-Za-z0-9_-]+")))
            Base64.getUrlDecoder().decode(part).also {
                check(Base64.getUrlEncoder().withoutPadding().encodeToString(it) == part)
            }
        }
        val header = TemporarySuccessJson.parse(decoded[0])
        check(header.keys().asSequence().toSet() == setOf("alg", "typ", "kid"))
        val claims = TemporarySuccessJson.parse(decoded[1])
        check(claims.keys().asSequence().toSet() == setOf("iss", "aud", "receipt"))
        JwtVerifier.verify(token, key, "acrylic-pda-receipt", 262144)
        val unsigned = JSONObject(receipt.toString()).apply { remove("signedReceipt") }
        check(SubmissionJson.canonical(claims.getJSONObject("receipt")) == SubmissionJson.canonical(unsigned)) {
            "成功原回执与签名不一致"
        }
    }

    private fun context(local: TemporarySubmission, body: JSONObject, value: JSONObject) {
        check(value.keys().asSequence().toSet() == setOf("submissionId", "orderId", "status", "payloadSha256",
            "freezeDeviceId", "workRound", "issuedRecoveryEpoch", "mayResumeScanning", "stockReleased", "source",
            "orderStatus", "actualCount", "actualWeightGrams", "inventoryDeducted", "resultVersion", "receiptVersion",
            "captureHighWatermark", "firstOrderScanSeq", "lastOrderScanSeq", "completedAt", "orderNo", "items", "signedReceipt"))
        mapOf("submissionId" to local.id, "payloadSha256" to local.sha256, "status" to "SUCCEEDED",
            "orderStatus" to "COMPLETED", "resultVersion" to "1", "workRound" to body.get("workRound"),
            "freezeDeviceId" to body.get("freezeDeviceId"), "issuedRecoveryEpoch" to body.get("sourceRecoveryEpoch"),
            "captureHighWatermark" to body.get("captureHighWatermark"), "actualCount" to body.get("expectedCount"),
            "actualWeightGrams" to body.get("expectedWeightGrams"), "inventoryDeducted" to body.get("autoDeductInventory"),
            "mayResumeScanning" to false, "stockReleased" to false).forEach { (field, expected) ->
            check(value.get(field) == expected) { "成功回执与冻结记录不符：$field" }
        }
        check(value.get("receiptVersion") == 1L)
        check(SubmissionJson.canonical(value.getJSONObject("source")) == SubmissionJson.canonical(body.getJSONObject("source")))
        TemporaryVerifier.uuid(value.get("orderId") as String)
        check((value.get("orderNo") as String).isNotBlank()); Instant.parse(value.get("completedAt") as String)
        local.receipt?.let { previous ->
            val old = JSONObject(previous)
            check(old.optString("status") != "ABORTED") { "本地已存取消终态，须保留冲突原件" }
            if (!old.isNull("orderId")) check(old.get("orderId") == value.get("orderId")) { "成功目标与原待审目标不符" }
        }
    }
}
