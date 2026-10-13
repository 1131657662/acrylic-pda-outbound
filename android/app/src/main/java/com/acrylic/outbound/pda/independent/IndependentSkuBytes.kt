package com.acrylic.outbound.pda.independent

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

internal object IndependentSkuBytes {
    const val CHUNK_SIZE = 128 * 1024
    fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
    fun read(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return output.toByteArray()
            check(output.size() + count <= IndependentSkuParser.MAX_BYTES) { "文件不得超过2MiB；本次未保存或启用，旧资料保留" }
            output.write(buffer, 0, count)
        }
    }
    fun original(dao: IndependentSkuDao, sha: String): ByteArray {
        val saved = checkNotNull(dao.original(sha)) { "资料原件不存在，请保全本机数据库" }
        check(saved.parserVersion == IndependentSkuParser.VERSION) { "原资料解析版本不支持，请保全原件" }
        check(saved.byteLength in 0..IndependentSkuParser.MAX_BYTES) { "原资料长度不一致" }
        val chunks = dao.chunks(sha)
        val output = ByteArrayOutputStream(saved.byteLength)
        chunks.forEachIndexed { index, chunk ->
            check(chunk.part == index && chunk.bytes.size in 1..CHUNK_SIZE) { "原资料分块不完整" }
            check(output.size() + chunk.bytes.size <= saved.byteLength) { "原资料长度不一致" }
            output.write(chunk.bytes)
        }
        val bytes = output.toByteArray()
        check(bytes.size == saved.byteLength && hash(bytes) == sha) { "原资料摘要不一致，请停用并保全原件" }
        return bytes
    }
}
