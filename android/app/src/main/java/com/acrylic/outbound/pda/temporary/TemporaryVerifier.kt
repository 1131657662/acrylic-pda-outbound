package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

object TemporaryVerifier {
    val parts = setOf("inventory", "mapping", "policy", "rules", "availability")
    fun manifest(receipt: JSONObject, key: JSONObject): JSONObject {
        val text = receipt.getString("manifest")
        check(text.toByteArray(Charsets.UTF_8).size <= 65536) { "临时清单过大" }
        val sha = JwtVerifier.sha(text.toByteArray(Charsets.UTF_8))
        check(sha == receipt.getString("manifestSha256")) { "临时清单摘要不一致" }
        val manifest = JSONObject(text); val identity = manifest.getJSONObject("identity")
        check(manifest.get("schemaVersion") == 1 && manifest.getString("purpose") == "TEMP_WORK") { "临时资料用途错误" }
        uuid(manifest.getString("inventorySnapshotId")); positive(manifest.getString("inventoryVersion")); positive(manifest.getString("policyVersion"))
        check(manifest.getString("mappingRevision").matches(Regex("0|[1-9][0-9]{0,17}")))
        for (field in listOf("authorizationId", "bundleId")) {
            check(uuid(receipt.getString(field)) == identity.getString(field)) { "临时资料标识不一致" }
        }
        val claims = JwtVerifier.verify(receipt.getString("offlineToken"), key, "acrylic-pda-temporary-work")
        val signed = JwtVerifier.verify(receipt.getString("signedManifest"), key, "acrylic-pda-temporary-preparation")
        check(claims.getString("jti") == receipt.getString("authorizationId") && claims.getString("purpose") == "TEMP_WORK")
        check(claims.getString("scope") == "temporary_order:create pda:scan pda:submit") { "临时资格权限错误" }
        identityProof(identity, claims.getJSONObject("identity"))
        for (field in listOf("authorizationId", "bundleId")) check(signed.getString(field) == receipt.getString(field))
        check(claims.getString("manifestSha256") == sha && signed.getString("manifestSha256") == sha)
        val issued = Instant.parse(manifest.getString("issuedAt")); val expires = Instant.parse(manifest.getString("expiresAt"))
        check(issued.nano == 0 && expires.nano == 0 && expires.epochSecond - issued.epochSecond == 172800L) { "临时资格期限错误" }
        for (proof in listOf(claims, signed)) check(proof.getLong("iat") == issued.epochSecond && proof.getLong("exp") == expires.epochSecond)
        descriptors(manifest); return manifest
    }
    private fun identityProof(identity: JSONObject, signed: JSONObject) {
        val fields = setOf("authorizationId", "bundleId", "userId", "deviceId", "warehouseId", "authorizationEpoch",
            "userAuthVersion", "deviceVersion", "serverRecoveryEpoch")
        check(identity.keys().asSequence().toSet() == fields && signed.keys().asSequence().toSet() == fields)
        fields.forEach { check(identity.get(it) is String && identity.get(it) == signed.get(it)) { "临时资格身份不一致" } }
    }
    fun identity(manifest: JSONObject, auth: JSONObject) {
        val identity = manifest.getJSONObject("identity")
        for (field in listOf("userId", "deviceId", "warehouseId", "userAuthVersion", "deviceVersion", "serverRecoveryEpoch"))
            check(identity.getString(field) == auth.getString(field)) { "临时资料身份或授权版本变化，请保留原资料" }
        for (field in listOf("userId", "deviceId", "warehouseId")) uuid(identity.getString(field))
        for (field in listOf("authorizationEpoch", "userAuthVersion", "deviceVersion", "serverRecoveryEpoch")) positive(identity.getString(field))
    }
    fun time(manifest: JSONObject, now: Instant) {
        check(!now.isBefore(Instant.parse(manifest.getString("issuedAt"))) && now.isBefore(Instant.parse(manifest.getString("expiresAt")))) {
            "临时资格未生效或已到期，原资料保留"
        }
    }
    fun request(manifest: JSONObject, row: TemporaryRequest) {
        val command = JSONObject(row.bodyJson)
        for (field in listOf("inventorySnapshotId", "mappingRevision", "policyVersion")) check(command.getString(field) == manifest.getString(field)) { "临时资料与原准备请求不一致" }
    }
    fun descriptors(manifest: JSONObject): List<JSONObject> {
        val array = manifest.getJSONArray("parts"); check(array.length() == 5) { "临时资料必须包含五份文件" }
        val result = List(array.length()) { array.getJSONObject(it) }
        check(result.map { it.getString("name") }.toSet() == parts) { "临时资料文件名错误或重复" }
        result.forEach {
            for (field in listOf("bytes", "compressedBytes", "rows")) {
                val number = it.get(field); check(number is Int || number is Long)
                check((number as Number).toLong() in 0..Int.MAX_VALUE.toLong())
            }
            check(it.getInt("bytes") in 1..16_777_216 && it.getInt("compressedBytes") in 1..16_777_216 && it.getInt("rows") >= 0)
            for (field in listOf("sha256", "compressedSha256")) check(it.getString(field).matches(Regex("[0-9a-f]{64}")))
        }
        return result
    }
    fun uuid(text: String): String = UUID.fromString(text).toString().also { check(it == text) { "资料标识格式错误" } }
    fun positive(text: String): Long = text.toLong().also { check(it > 0 && it.toString() == text) { "资料版本或周期错误" } }
}
