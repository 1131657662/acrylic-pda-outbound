package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.preparation.PackageVerifier
import org.json.JSONObject
import java.io.File

class TemporaryDownload(private val cache: File, private val db: PdaDatabase, private val connection: TemporaryConnection) {
    fun fetch(request: TemporaryRequest, expected: JSONObject, progress: (String) -> Unit): Map<String, ByteArray> {
        val receipt = JSONObject(checkNotNull(request.receiptJson))
        val manifest = TemporaryVerifier.manifest(receipt, JSONObject(request.signingKeyJson))
        TemporaryVerifier.identity(manifest, expected); TemporaryVerifier.request(manifest, request)
        val directory = File(cache, TemporaryVerifier.uuid(request.key)).apply { check(isDirectory || mkdirs()) { "无法创建下载目录" } }
        return TemporaryVerifier.descriptors(manifest).mapIndexed { index, part ->
            TemporaryAccess(db, connection).request(request, expected); TemporaryVerifier.time(manifest, connection.now())
            progress("正在下载并校验临时资料 ${index + 1}/5")
            val name = part.getString("name"); val file = File(directory, "$name.gz")
            val existing = if (file.exists()) runCatching { PackageVerifier.unpack(file, part) }.getOrNull() else null
            name to (existing ?: download(request, expected, part, file))
        }.toMap()
    }
    private fun download(request: TemporaryRequest, expected: JSONObject, part: JSONObject, target: File): ByteArray {
        val receipt = JSONObject(checkNotNull(request.receiptJson)); val id = TemporaryVerifier.uuid(receipt.getString("authorizationId"))
        val bundle = TemporaryVerifier.uuid(receipt.getString("bundleId")); val name = part.getString("name")
        val partial = File(target.parentFile, "$name.partial")
        connection.download(TemporaryConnection.Download("pda/temporary-preparations/$id/content/$name?bundleId=$bundle", expected, partial))
        val bytes = PackageVerifier.unpack(partial, part)
        check(!target.exists() || target.delete()) { "无法替换损坏的下载缓存" }
        check(partial.renameTo(target)) { "无法保存已校验下载文件" }
        return bytes
    }
}
