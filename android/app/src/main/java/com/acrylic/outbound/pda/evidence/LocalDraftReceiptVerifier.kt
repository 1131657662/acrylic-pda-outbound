package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import java.time.Instant
import java.util.Base64

/** A storage receipt authenticates original bytes; it never authorizes business work or local deletion. */
object LocalDraftReceiptVerifier {
    const val AUDIENCE = "acrylic-r06-local-draft-evidence-v1"
    data class Expected(val stop: LocalDraftStop, val request: LocalDraftRequest, val counts: Counts)
    data class Counts(val own: Long, val other: Long, val archives: Long)
    fun verify(expected: Expected, bytes: ByteArray, key: JSONObject): JSONObject {
        check(bytes.size in 1..262144)
        val receipt = JSONObject(bytes.toString(Charsets.UTF_8))
        check(SubmissionJson.canonical(receipt).toByteArray(Charsets.UTF_8).contentEquals(bytes))
        val token = receipt.getString("signedReceipt"); token.split('.').forEach {
            check(it.matches(Regex("[A-Za-z0-9_-]+")))
            check(Base64.getUrlEncoder().withoutPadding().encodeToString(Base64.getUrlDecoder().decode(it)) == it)
        }
        val claims = JwtVerifier.verify(token, key, AUDIENCE, 262144)
        check(claims.keys().asSequence().toSet() == setOf("iss", "aud", "receipt"))
        val unsigned = JSONObject(receipt.toString()).apply { remove("signedReceipt") }
        check(SubmissionJson.canonical(claims.getJSONObject("receipt")) == SubmissionJson.canonical(unsigned))
        val expectedReceipt = common(expected, receipt.getString("recordedAt"))
        expectedReceipt.put("detail", detail(expected))
        check(SubmissionJson.canonical(unsigned) == SubmissionJson.canonical(expectedReceipt)) { "原回执来源、原请求或能力不一致" }
        return receipt
    }
    private fun common(expected: Expected, time: String): JSONObject {
        Instant.parse(time)
        val stop = expected.stop; val request = expected.request; val draft = JSONObject(stop.metadataJson).getJSONObject("draft")
        check(request.clientOrderId == stop.clientOrderId)
        check(E2aWire.sha(request.requestJson.toByteArray(Charsets.UTF_8)) == request.requestSha256)
        return JSONObject().put("receiptVersion", 1).put("operationKind", request.operation).put("originalKey", request.originalKey)
            .put("evidenceId", stop.evidenceId).put("clientOrderId", stop.clientOrderId).put("warehouseId", draft.getString("warehouseId"))
            .put("creatorDeviceId", draft.getString("creatorDeviceId")).put("originalOperatorId", draft.getString("operatorId"))
            .put("rootSha256", stop.rootSha256).put("requestSha256", request.requestSha256).put("recordedAt", time)
            .put("status", if (request.operation == "VERIFY") "ORIGINALS_STORED" else "RECEIVED").put("businessFactsStatus", "NOT_EVALUATED")
            .put("localDeletionAllowed", false).put("releaseAllowed", false).put("mayResumeScanning", false).put("writeConfirmationAvailable", false)
    }
    private fun detail(expected: Expected): JSONObject = when (expected.request.operation) {
        "OPEN" -> JSONObject()
        "ITEM" -> JSONObject().put("ordinal", expected.request.position)
            .put("bytesSha256", JSONObject(expected.request.requestJson).getString("bytesSha256"))
        "VERIFY" -> JSONObject().put("recordCount", expected.stop.recordCount).put("byteCount", expected.stop.totalBytes)
            .put("ownCaptureCount", expected.counts.own).put("otherCaptureCount", expected.counts.other).put("archiveCount", expected.counts.archives)
        else -> error("不支持的原回执类型")
    }
}
