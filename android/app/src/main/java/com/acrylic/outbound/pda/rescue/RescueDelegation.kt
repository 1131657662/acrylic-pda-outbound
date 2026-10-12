package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** A file never supplies its own authority key. Caller supplies an independently provisioned server key. */
internal object RescueDelegation {
    const val AUDIENCE = "acrylic-pda-readonly-rescue-v1"
    data class Verified(val grantId: String, val rescuerId: String, val issuerId: String,
        val identity: RescueReadIdentity, val deviceKeySha256: String, val targets: Set<RescueTarget>,
        val issuedAt: Instant, val expiresAt: Instant?, val originalToken: String) {
        fun validAt(now: Instant?) {
            check(now == null || now >= issuedAt) { "主管救援尚未生效，请保留原件" }
            if (expiresAt == null) return
            check(now != null && now >= issuedAt && now < expiresAt) { "主管救援期限到期或当前时间未证实，请保留原件" }
        }
    }
    fun verify(token: String, trustedKey: JSONObject, deviceKey: Pair<String, String>): Verified {
        val value = claims(token, trustedKey)
        check(value.keys().asSequence().toSet() == setOf("iss", "aud", "purpose", "grantId", "rescuerId", "issuerId",
            "deviceId", "operatorId", "warehouseId", "deviceKeySha256", "orderScope", "issuedAt", "expiresAt", "capabilities"))
        check(value.getString("purpose") == "SUPERVISOR_READ_ONLY_RESCUE")
        capabilities(value.getJSONObject("capabilities"))
        val identity = RescueReadIdentity(uuid(value, "deviceId"), uuid(value, "operatorId"), uuid(value, "warehouseId"))
        val digest = value.getString("deviceKeySha256")
        check(digest.matches(Regex("[0-9a-f]{64}")) && deviceKey == (identity.deviceId to digest)) { "主管救援设备或登记钥不匹配" }
        val targets = scope(value)
        val issued = Instant.parse(value.getString("issuedAt"))
        val expires = if (value.isNull("expiresAt")) null else Instant.parse(value.getString("expiresAt"))
        check(expires == null || expires > issued)
        return Verified(uuid(value, "grantId"), uuid(value, "rescuerId"), uuid(value, "issuerId"), identity,
            digest, targets, issued, expires, token)
    }
    fun verifyRevocation(token: String, trustedKey: JSONObject, authorization: Verified): Instant {
        val value = claims(token, trustedKey)
        check(value.keys().asSequence().toSet() == setOf("iss", "aud", "purpose", "rescuerId", "deviceId",
            "operatorId", "warehouseId", "deviceKeySha256", "grantId", "issuerId", "revokedAt", "authorizationSha256"))
        check(value.getString("purpose") == "SUPERVISOR_READ_ONLY_RESCUE_REVOCATION")
        check(uuid(value, "grantId") == authorization.grantId && uuid(value, "rescuerId") == authorization.rescuerId &&
            uuid(value, "deviceId") == authorization.identity.deviceId && uuid(value, "operatorId") == authorization.identity.operatorId &&
            uuid(value, "warehouseId") == authorization.identity.warehouseId &&
            value.getString("deviceKeySha256") == authorization.deviceKeySha256 &&
            value.getString("authorizationSha256") == JwtVerifier.sha(authorization.originalToken.toByteArray(Charsets.UTF_8))) {
            "撤销凭据不属于原授权，请保留原件"
        }
        uuid(value, "issuerId")
        return Instant.parse(value.getString("revokedAt")).also { check(it >= authorization.issuedAt) }
    }
    private fun claims(token: String, trustedKey: JSONObject): JSONObject {
        check(token.length in 1..32_768)
        check(token.split('.').all { it.matches(Regex("[A-Za-z0-9_-]+")) })
        JwtVerifier.verify(token, trustedKey, AUDIENCE, 32_768)
        val parts = token.split('.'); val decoder = java.util.Base64.getUrlDecoder()
        val header = RescueSignedJson.read(decoder.decode(parts[0]))
        check(header.keys().asSequence().toSet() == setOf("alg", "typ", "kid"))
        return RescueSignedJson.read(decoder.decode(parts[1]))
    }
    private fun scope(value: JSONObject): Set<RescueTarget> {
        val ids = value.getJSONArray("orderScope")
        check(ids.length() in 1..100) { "主管救援必须限定非空订单范围" }
        val targets = (0 until ids.length()).map { index ->
            val row = ids.getJSONObject(index)
            check(row.keys().asSequence().toSet() == setOf("source", "localOrderId"))
            val source = row.getString("source"); check(source in setOf("FORMAL", "TEMP"))
            RescueTarget(source, uuid(row, "localOrderId"))
        }
        check(targets.toSet().size == targets.size) { "主管救援订单范围重复" }
        return targets.toSet()
    }
    private fun capabilities(value: JSONObject) {
        val closed = setOf("mayResumeScanning", "maySubmit", "releaseAllowed", "stockReleased", "localDeletionAllowed")
        check(value.keys().asSequence().toSet() == closed + "readOriginalAllowed")
        check(value.get("readOriginalAllowed") == true && closed.all { value.get(it) == false }) { "主管救援不得开放作业能力" }
    }
    private fun uuid(value: JSONObject, field: String) = canonicalUuid(value.getString(field))
    private fun canonicalUuid(text: String): String = text.also { check(UUID.fromString(it).toString() == it) }
}
