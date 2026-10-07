package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import java.time.Instant

/** Only checks the stored receipt's historical facts; never grants current business capabilities. */
object E2aReceiptVerifier {
    data class Expected(val stop: LocalE2aStop, val plan: LocalE2aTransfer, val request: LocalE2aRequest)
    fun verify(expected: Expected, bytes: ByteArray, trustedKey: JSONObject): JSONObject {
        check(bytes.size in 1..262144)
        val receipt = JSONObject(bytes.toString(Charsets.UTF_8))
        check(SubmissionJson.canonical(receipt).toByteArray(Charsets.UTF_8).contentEquals(bytes)) {
            "原回执不是完整规范字节"
        }
        val claims = JwtVerifier.verify(receipt.getString("signedReceipt"), trustedKey,
            "acrylic-r06-e2a-intake-v1", 262144).getJSONObject("receipt")
        val unsigned = JSONObject(receipt.toString()).apply { remove("signedReceipt") }
        check(SubmissionJson.canonical(claims) == SubmissionJson.canonical(unsigned)) { "原回执与签名不一致" }
        common(expected, receipt)
        operation(expected, receipt)
        return receipt
    }

    private fun common(expected: Expected, value: JSONObject) {
        val stop = expected.stop
        equal(value, "receiptVersion", 1)
        equal(value, "originalKey", expected.request.originalKey)
        equal(value, "operationKind", expected.request.operation)
        equal(value, "source", "TEMP"); equal(value, "sourceId", stop.authorizationId)
        equal(value, "submissionId", stop.submissionId); equal(value, "workRound", stop.workRound)
        equal(value, "manifestId", stop.manifestId)
        E2aItemValidation.uuid(value.getString("receiptId")); Instant.parse(value.getString("recordedAt"))
        val context = value.getJSONObject("originalContext")
        equal(context, "principalId", stop.originalOperatorId); equal(context, "originalOperatorId", stop.originalOperatorId)
        equal(context, "warehouseId", stop.warehouseId); equal(context, "creatorDeviceId", stop.freezeDeviceId)
        equal(context, "clientOrderId", stop.clientOrderId); equal(context, "temporaryAuthorizationId", stop.authorizationId)
        equal(context, "sourceRecoveryEpoch", stop.sourceRecoveryEpoch)
        listOf("scopeId", "currentSessionId").forEach { E2aItemValidation.uuid(context.getString(it)) }
        listOf("userAuthVersion", "deviceVersion").forEach { check(context.getString(it).toLong() > 0) }
        val capabilities = value.getJSONObject("capabilities")
        val closed = setOf("writeConfirmationAvailable", "releaseAllowed", "mayResumeScanning", "stockReleased", "localDeletionAllowed")
        check(capabilities.keys().asSequence().toSet() == closed)
        closed.forEach { check(capabilities.get(it) == false) { "原回执出现未支持能力" } }
    }

    private fun operation(expected: Expected, receipt: JSONObject) {
        val stop = expected.stop; val request = expected.request; val plan = expected.plan
        val body = JSONObject(request.requestJson)
        if (request.operation == "VERIFY") {
            check(receipt.getString("status") in setOf("PARTIAL", "VERIFIED_COMPLETE_AT_STOP")) { "原验证回执状态不支持" }
        } else equal(receipt, "status", if (request.operation == "OPEN") "RECEIVED" else "PARTIAL")
        if (request.operation != "VERIFY") equal(receipt, "manifestVersion", 1)
        if (request.operation == "OPEN" || request.operation == "PAGE") equal(receipt, "snapshotId", stop.snapshotId)
        if (request.operation != "OPEN") equal(receipt, "openOriginalKey", stop.openOriginalKey)
        when (request.operation) {
            "OPEN" -> Unit
            "PAGE" -> page(body, receipt)
            "STOP" -> {
                equal(receipt, "manifestBytesHash", plan.manifestBytesHash); equal(receipt, "setHash", plan.setHash)
                equal(receipt, "stopEventId", stop.stopEventId)
                objectBytes(receipt, SubmissionJson.canonical(body.getJSONObject("stopProof")))
            }
            "VERIFY" -> {
                equal(receipt, "stopOriginalKey", plan.stopOriginalKey); equal(receipt, "setHash", plan.setHash)
                equal(receipt, "verificationVersion", 1); equal(receipt, "algorithmVersion", 1)
                listOf("evidenceSetId", "evidenceVerificationId").forEach { E2aItemValidation.uuid(receipt.getString(it)) }
            }
            else -> error("不支持的原回执类型")
        }
    }

    private fun page(body: JSONObject, receipt: JSONObject) {
        val core = body.getJSONObject("pageCore")
        equal(receipt, "pageHash", body.getString("pageHash"))
        equal(receipt, "pageNo", core.getLong("pageNo")); equal(receipt, "pageCount", core.getLong("pageCount"))
        objectBytes(receipt, SubmissionJson.canonical(core))
    }

    private fun objectBytes(receipt: JSONObject, canonical: String) {
        E2aItemValidation.uuid(receipt.getString("objectId"))
        val bytes = canonical.toByteArray(Charsets.UTF_8)
        equal(receipt, "objectBytesSha256", E2aWire.sha(bytes)); equal(receipt, "objectByteCount", bytes.size)
    }

    private fun equal(value: JSONObject, field: String, expected: Any) {
        val actual = value.get(field)
        check(if (expected is Number) (actual is Int || actual is Long) && (actual as Number).toLong() == expected.toLong()
            else actual == expected) { "原回执来源或请求不匹配：$field" }
    }
}
