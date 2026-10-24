package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONObject
import java.time.Instant
import java.util.Base64

/** Historical display evidence. Never grants collection, submission, confirmation or deletion. */
internal object RescueOperatorNameProof {
    const val AUDIENCE = "acrylic-pda-operator-name-v1"
    data class Binding(val source: String, val authorizationId: String, val operatorId: String,
        val deviceId: String, val warehouseId: String, val originalToken: String, val issued: Instant)

    fun verify(token: String, key: JSONObject, binding: Binding): String {
        check(token.length in 1..16_384 && token.split('.').all { it.matches(Regex("[A-Za-z0-9_-]+")) })
        JwtVerifier.verify(token, key, AUDIENCE, 16_384)
        val parts = token.split('.'); val decoder = Base64.getUrlDecoder()
        val header = RescueSignedJson.read(decoder.decode(parts[0]))
        check(header.keys().asSequence().toSet() == setOf("alg", "typ", "kid"))
        val value = RescueSignedJson.read(decoder.decode(parts[1]))
        check(value.keys().asSequence().toSet() == setOf("iss", "aud", "purpose", "source", "authorizationId",
            "operatorId", "deviceId", "warehouseId", "originalSha256", "displayName", "issuedAt"))
        check(value.keys().asSequence().all { value.get(it) is String }) { "原姓名证明字段类型无效" }
        check(value.getString("purpose") == "READONLY_OPERATOR_NAME" && binding.source in setOf("TEMP", "INDEPENDENT"))
        val expected = mapOf("source" to binding.source, "authorizationId" to binding.authorizationId,
            "operatorId" to binding.operatorId, "deviceId" to binding.deviceId, "warehouseId" to binding.warehouseId,
            "originalSha256" to JwtVerifier.sha(binding.originalToken.toByteArray(Charsets.UTF_8)))
        expected.forEach { (field, original) -> check(value.getString(field) == original) { "原姓名证明上下文不一致：$field" } }
        check(Instant.parse(value.getString("issuedAt")) == binding.issued) { "原姓名证明发行时间不一致" }
        return value.getString("displayName").also { check(it.isNotBlank()) { "原操作员姓名缺失" } }
    }
}
