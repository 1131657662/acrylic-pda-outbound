package com.acrylic.outbound.pda.security

import org.json.JSONObject
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64

object JwtVerifier {
    fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun verify(token: String, key: JSONObject, audience: String, maximumLength: Int = 32768,
        issuer: String = "acrylic-outbound"): JSONObject {
        val parts = token.split('.')
        check(parts.size == 3 && token.length <= maximumLength) { "授权签名格式错误" }
        val decoder = Base64.getUrlDecoder()
        val header = JSONObject(String(decoder.decode(parts[0]), Charsets.UTF_8))
        check(header.getString("alg") == "RS256" && header.getString("typ") == "JWT") { "不支持的授权算法" }
        check(key.getString("algorithm") == "RS256" && header.getString("kid") == key.getString("keyId")) { "授权公钥不匹配" }
        val public = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(key.getString("publicKey")))) as RSAPublicKey
        check(public.modulus.bitLength() >= 2048) { "授权公钥强度不足" }
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(public); verifier.update("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII))
        check(verifier.verify(decoder.decode(parts[2]))) { "授权签名校验失败" }
        return JSONObject(String(decoder.decode(parts[1]), Charsets.UTF_8)).also {
            check(it.getString("iss") == issuer && it.getString("aud") == audience) { "授权用途不匹配" }
        }
    }

    fun authorization(assignment: JSONObject, session: JSONObject, key: JSONObject, now: Instant,
        acceptedScopes: Set<String> = setOf("pda:scan")): JSONObject {
        val claims = verify(assignment.getString("offlineToken"), key, "acrylic-pda-offline")
        val fields = mapOf("assignmentId" to "id", "jti" to "offlineAuthorizationId", "sub" to "operatorId",
            "assignmentEpoch" to "epoch", "deviceId" to "deviceId", "orderId" to "orderId", "revision" to "revision",
            "inventorySnapshotId" to "inventorySnapshotId", "serverRecoveryEpoch" to "serverRecoveryEpoch",
            "planSha256" to "planSha256", "inventorySha256" to "inventorySha256",
            "startOrderScanSeq" to "startOrderScanSeq", "allocationVersion" to "allocationVersion")
        fields.forEach { (claim, field) -> check(claims.get(claim).toString() == assignment.get(field).toString()) { "授权上下文不匹配：$claim" } }
        check(claims.getString("sub") == session.getString("userId") && claims.getString("deviceId") == session.getString("deviceId") &&
            claims.getString("warehouseId") == session.getString("warehouseId")) { "授权不属于当前人员或设备" }
        check(claims.getString("scope") in acceptedScopes && assignment.getString("status") == "ACTIVE") { "授权用途不匹配或已停止" }
        check(claims.getString("serverRecoveryEpoch") == session.getString("serverRecoveryEpoch")) { "恢复代次变化，请保留资料并对账" }
        check(claims.getString("userAuthVersion") == session.getString("userAuthVersion") && claims.getString("deviceVersion") == session.getString("deviceVersion")) { "人员或设备授权版本变化，须重新授权" }
        val issued = claims.getLong("iat"); val expires = claims.getLong("exp")
        check(expires - issued == 172800L && issued <= now.epochSecond && now.epochSecond < expires) { "离线授权未生效或已到期，订单占用保留" }
        check(Instant.parse(assignment.getString("issuedAt")).epochSecond == issued && Instant.parse(assignment.getString("expiresAt")).epochSecond == expires)
        return claims
    }
}
