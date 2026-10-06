package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import java.util.Base64

object TemporaryContinuationProtocol {
    data class Input(val draft: TemporaryDraft, val frozen: TemporarySubmission, val manifest: JSONObject,
        val session: JSONObject, val key: JSONObject)
    data class Verified(val ticketId: String, val newClientOrderId: String, val authorization: JSONObject,
        val toRound: Long, val nextOrderScanSeq: Long, val receiptJson: String)

    fun verify(original: ByteArray, input: Input): Verified {
        val response = TemporarySuccessJson.parse(original)
        check(SubmissionJson.canonical(response).toByteArray(Charsets.UTF_8).contentEquals(original)) { "续作原回执字节不规范" }
        signature(response, input.key)
        envelope(response)
        check(response.get("receiptVersion") == 1L && response.get("routeScope") == "r06.command.v1")
        check(response.get("status") == "RECORDED" && response.get("commandType") == "ACTIVATE_CONTINUATION") { "续作回执类型无效" }
        closedCapabilities(response.getJSONObject("capabilities"))
        TemporaryContinuationSource.verify(response, input)
        val decision = response.getJSONObject("businessDecision")
        check(decision.keys().asSequence().toSet() == setOf("kind", "status", "ticketId", "fromWorkRound", "toWorkRound",
            "nextOrderScanSeq", "newAuthorizationId", "newBundleId", "newClientOrderId", "targetDeviceId", "targetOperatorId",
            "scopeId", "retainedManifestSha256", "retainedManifestVersion", "authorization", "retained"))
        for (field in listOf("retainedManifestSha256", "retainedManifestVersion"))
            check(response.getJSONObject("expected").get(field) == decision.get(field))
        check(decision.get("kind") == "ACTIVATE_CONTINUATION" && decision.get("status") == "ACTIVATED") { "续作未激活" }
        val ticket = uuid(decision, "ticketId"); val from = positive(decision, "fromWorkRound")
        val to = positive(decision, "toWorkRound"); check(to == Math.addExact(from, 1) && from == input.draft.workRound) { "续作轮次不连续" }
        val next = positive(decision, "nextOrderScanSeq"); check(next == input.draft.nextScanSeq) { "续作起始序号不匹配" }
        check(uuid(decision, "targetDeviceId") == input.session.get("deviceId") && uuid(decision, "targetOperatorId") == input.session.get("userId")) { "续作目标身份不匹配" }
        check(uuid(decision, "scopeId") == response.getJSONObject("originalContext").get("scopeId"))
        val client = uuid(decision, "newClientOrderId"); check(client != input.draft.clientOrderId)
        authorization(decision, input)
        TemporaryContinuationSource.retained(decision, input.frozen)
        return Verified(ticket, client, decision.getJSONObject("authorization"), to, next, SubmissionJson.canonical(response))
    }

    private fun authorization(decision: JSONObject, input: Input) {
        val authorization = decision.getJSONObject("authorization")
        check(authorization.keys().asSequence().toSet() == setOf("authorizationId", "bundleId", "offlineToken", "manifest", "manifestSha256", "signedManifest"))
        val manifest = TemporaryVerifier.manifest(authorization, input.key)
        TemporaryVerifier.identity(manifest, input.session)
        check(uuid(authorization, "authorizationId") == uuid(decision, "newAuthorizationId")) { "续作新资格不匹配" }
        check(authorization.get("authorizationId") != input.draft.authorizationId) { "续作不得复用原资格" }
        check(uuid(authorization, "bundleId") == uuid(decision, "newBundleId"))
        check(authorization.get("bundleId") != input.manifest.getJSONObject("identity").get("bundleId"))
        val oldEpoch = positive(input.manifest.getJSONObject("identity"), "authorizationEpoch")
        val newEpoch = positive(manifest.getJSONObject("identity"), "authorizationEpoch")
        check(newEpoch > oldEpoch) { "续作资格代次未递增" }
        for (field in listOf("inventorySnapshotId", "inventoryVersion", "mappingRevision", "policyVersion"))
            check(manifest.get(field) == input.manifest.get(field)) { "续作不得替换原资料" }
        val oldParts = TemporaryVerifier.descriptors(input.manifest).associate { it.getString("name") to SubmissionJson.canonical(it) }
        val newParts = TemporaryVerifier.descriptors(manifest).associate { it.getString("name") to SubmissionJson.canonical(it) }
        check(oldParts == newParts) { "续作原资料摘要变化" }
    }

    private fun signature(response: JSONObject, key: JSONObject) {
        val token = response.get("signedReceipt") as String; val parts = token.split('.')
        check(parts.size == 3 && token.length <= 262144)
        val decoded = parts.map { part ->
            check(part.matches(Regex("[A-Za-z0-9_-]+")))
            Base64.getUrlDecoder().decode(part).also { check(Base64.getUrlEncoder().withoutPadding().encodeToString(it) == part) }
        }
        val header = TemporarySuccessJson.parse(decoded[0])
        check(header.keys().asSequence().toSet() == setOf("alg", "typ", "kid"))
        val claims = TemporarySuccessJson.parse(decoded[1])
        check(claims.keys().asSequence().toSet() == setOf("iss", "aud", "receipt"))
        JwtVerifier.verify(token, key, "acrylic-r06-command-receipt-v1", 262144)
        val unsigned = JSONObject(response.toString()).apply { remove("signedReceipt") }
        check(SubmissionJson.canonical(unsigned) == SubmissionJson.canonical(claims.getJSONObject("receipt"))) { "续作回执与签名不符" }
    }

    private fun envelope(receipt: JSONObject) {
        check(receipt.keys().asSequence().toSet() == setOf("receiptVersion", "receiptId", "commandId", "routeScope",
            "commandType", "commandSha256", "status", "businessDecision", "recordedAt", "eventId", "resultingDecisionVersion",
            "originalContext", "actor", "expected", "capabilities", "signedReceipt"))
        for (field in listOf("receiptId", "commandId", "eventId")) uuid(receipt, field)
        positive(receipt, "resultingDecisionVersion")
        check((receipt.get("commandSha256") as String).matches(Regex("[0-9a-f]{64}")))
        val at = java.time.Instant.parse(receipt.get("recordedAt") as String)
        check(at.toString() == receipt.get("recordedAt") && at.nano % 1_000_000 == 0)
    }

    private fun closedCapabilities(value: JSONObject) {
        val fields = setOf("writeConfirmationAvailable", "releaseAllowed", "mayResumeScanning", "stockReleased", "localDeletionAllowed")
        check(value.keys().asSequence().toSet() == fields && fields.all { value.get(it) == false }) { "续作回执不得开放生产能力" }
    }
    internal fun uuid(node: JSONObject, field: String): String = (node.get(field) as String).also { TemporaryVerifier.uuid(it) }
    internal fun positive(node: JSONObject, field: String): Long = TemporaryVerifier.positive(node.get(field) as String)
}
