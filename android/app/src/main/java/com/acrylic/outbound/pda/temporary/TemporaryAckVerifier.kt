package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONObject
import java.time.Instant

object TemporaryAckVerifier {
    // Matches the server Ack record field order and Instant serializer; never hashes a reordered JSONObject.
    fun commandDigest(body: JSONObject): String {
        val fields = listOf("bundleId", "manifestSha256", "authorizationEpoch", "activatedAt")
        val text = fields.joinToString(",", "{", "}") { field ->
            val value = if (field == "activatedAt") Instant.parse(body.getString(field)).toString() else body.getString(field)
            JSONObject.quote(field) + ":" + JSONObject.quote(value)
        }
        return JwtVerifier.sha(text.toByteArray(Charsets.UTF_8))
    }
    fun declaration(row: TemporaryPreparation, request: TemporaryRequest): JSONObject {
        val manifest = TemporaryHandoverAuthorization.declaration(request)
        val body = JSONObject(row.ackBodyJson); TemporaryVerifier.request(manifest, request)
        check(row.requestKey == request.key && row.authorizationId == manifest.getJSONObject("identity").getString("authorizationId"))
        check(row.bundleId == manifest.getJSONObject("identity").getString("bundleId") && row.manifestSha256 == JSONObject(checkNotNull(request.receiptJson)).getString("manifestSha256"))
        check(body.keys().asSequence().toSet() == setOf("bundleId", "manifestSha256", "authorizationEpoch", "activatedAt"))
        check(body.getString("bundleId") == row.bundleId && body.getString("manifestSha256") == row.manifestSha256)
        check(Instant.parse(body.getString("activatedAt")).truncatedTo(java.time.temporal.ChronoUnit.MILLIS) == Instant.parse(row.activatedAt))
        check(body.getString("authorizationEpoch") == manifest.getJSONObject("identity").getString("authorizationEpoch"))
        TemporaryVerifier.time(manifest, Instant.parse(row.activatedAt))
        return manifest
    }
    fun verify(row: TemporaryPreparation, request: TemporaryRequest, ack: JSONObject) {
        declaration(row, request)
        val body = JSONObject(row.ackBodyJson)
        val proof = JwtVerifier.verify(ack.getString("signedAck"), JSONObject(request.signingKeyJson), "acrylic-pda-temporary-activation")
        val expected = mapOf("authorizationId" to row.authorizationId, "bundleId" to row.bundleId, "manifestSha256" to row.manifestSha256,
            "commandSha256" to commandDigest(body), "serverRecoveryEpoch" to request.recoveryEpoch, "userId" to request.userId, "deviceId" to request.deviceId)
        expected.forEach { (field, value) -> check(proof.getString(field) == value) { "临时激活回执上下文不一致" } }
        for (field in listOf("authorizationId", "bundleId", "activatedAt", "acknowledgedAt")) check(ack.getString(field) == proof.getString(field))
        check(ack.getString("activatedAt") == row.activatedAt)
        val activated = Instant.parse(row.activatedAt)
        check(!Instant.parse(ack.getString("acknowledgedAt")).isBefore(activated.minusSeconds(300))) { "临时激活回执时间错误" }
    }
}
