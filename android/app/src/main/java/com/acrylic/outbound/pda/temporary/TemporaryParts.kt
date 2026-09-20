package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONObject

class TemporaryParts(private val db: PdaDatabase) {
    fun insert(id: String, payloads: Map<String, ByteArray>) {
        check(db.inTransaction()) { "临时原文须与激活同事务保存" }
        payloads.forEach { (name, bytes) ->
            var offset = 0; var ordinal = 0
            while (offset < bytes.size) {
                val end = minOf(offset + CHUNK, bytes.size)
                db.temporary().insertChunk(TemporaryPartChunk(id, name, ordinal++, bytes.copyOfRange(offset, end))); offset = end
            }
        }
    }
    fun read(id: String, manifest: JSONObject): Map<String, ByteArray> = TemporaryVerifier.descriptors(manifest).associate { part ->
        val name = part.getString("name"); val length = part.getInt("bytes"); val count = (length + CHUNK - 1) / CHUNK
        check(db.temporary().count(id, name) == count) { "本机临时原文分块不完整，保留资料" }
        val bytes = ByteArray(length)
        repeat(count) { index ->
            val chunk = checkNotNull(db.temporary().chunk(id, name, index)) { "本机临时原文有缺口" }
            check(chunk.bytes.size == minOf(CHUNK, length - index * CHUNK)) { "本机临时原文分块大小错误" }
            chunk.bytes.copyInto(bytes, index * CHUNK)
        }
        check(JwtVerifier.sha(bytes) == part.getString("sha256")) { "本机临时原文摘要错误，保留资料" }; name to bytes
    }
    companion object { const val CHUNK = 128 * 1024 }
}
