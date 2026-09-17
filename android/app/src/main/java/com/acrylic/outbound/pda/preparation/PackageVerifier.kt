package com.acrylic.outbound.pda.preparation

import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.zip.GZIPInputStream

object PackageVerifier {
    fun manifest(receipt: JSONObject, assignment: JSONObject, key: JSONObject, now: Instant): JSONObject {
        val text = receipt.getString("manifest")
        val sha = JwtVerifier.sha(text.toByteArray(Charsets.UTF_8))
        check(sha == receipt.getString("manifestSha256")) { "下载清单摘要错误" }
        val proof = JwtVerifier.verify(receipt.getString("signedManifest"), key, "acrylic-pda-preparation")
        check(proof.getString("manifestSha256") == sha && proof.getString("assignmentId") == assignment.getString("id")) { "下载清单签名上下文错误" }
        check(proof.getLong("exp") == Instant.parse(assignment.getString("expiresAt")).epochSecond && now.epochSecond < proof.getLong("exp")) { "下载授权已到期" }
        val manifest = JSONObject(text)
        check(manifest.getInt("schemaVersion") == 1 && manifest.getString("bundleId") == receipt.getString("bundleId"))
        val fields = mapOf("assignmentId" to "id", "assignmentEpoch" to "epoch", "orderId" to "orderId", "operatorId" to "operatorId",
            "deviceId" to "deviceId", "serverRecoveryEpoch" to "serverRecoveryEpoch", "inventorySnapshotId" to "inventorySnapshotId", "revision" to "revision")
        fields.forEach { (name, source) -> check(manifest.get(name).toString() == assignment.get(source).toString()) { "下载清单与订单不匹配" } }
        val parts = manifest.getJSONArray("parts")
        check(parts.length() == 3 && (0 until 3).map { parts.getJSONObject(it).getString("name") }.toSet() == setOf("plan", "inventory", "availability"))
        return manifest
    }

    fun unpack(file: File, part: JSONObject): ByteArray {
        val compressedSize = part.getLong("compressedBytes"); val expectedSize = part.getLong("bytes")
        check(compressedSize in 1..16_777_216 && expectedSize in 1..16_777_216) { "下载大小超出范围" }
        check(file.length() == compressedSize) { "下载未完成，请重试" }
        val compressed = file.readBytes()
        check(JwtVerifier.sha(compressed) == part.getString("compressedSha256")) { "压缩包校验失败" }
        val output = java.io.ByteArrayOutputStream()
        GZIPInputStream(compressed.inputStream()).use { input ->
            val buffer = ByteArray(8192)
            while (true) { val n = input.read(buffer); if (n < 0) break
                check(output.size().toLong() + n <= expectedSize) { "解压数据超过清单大小" }; output.write(buffer, 0, n) }
        }
        return output.toByteArray().also {
            check(it.size.toLong() == expectedSize && JwtVerifier.sha(it) == part.getString("sha256")) { "解压内容校验失败" }
        }
    }
}
