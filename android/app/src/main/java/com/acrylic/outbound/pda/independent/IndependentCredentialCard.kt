package com.acrylic.outbound.pda.independent

import android.util.JsonReader
import android.util.JsonToken
import java.io.InputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import org.json.JSONObject

/** Untrusted password transport; never a signed identity or a collection grant. */
internal class IndependentCredentialCard private constructor(private val binding: Map<String, String>,
    private val secret: CharArray) : AutoCloseable {
    private var closed = false
    fun credential(expected: JSONObject): CharArray {
        check(!closed && binding.all { (key, value) -> expected.optString(key) == value }) {
            "凭据卡与原人员、设备、仓库或登记钥不一致"
        }
        return secret.copyOf()
    }
    override fun close() { closed = true; secret.fill('\u0000') }

    companion object {
        private val fields = setOf("format", "purpose", "deviceId", "operatorId", "warehouseId",
            "deviceKeySha256", "credential", "custody")
        fun read(input: InputStream): IndependentCredentialCard {
            val buffer = ByteArray(4097)
            try {
                val size = readBounded(input, buffer)
                val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                return parse(decoder.decode(ByteBuffer.wrap(buffer, 0, size)).toString())
            } catch (_: Exception) { error("独立凭据卡格式无效或读取未完成，请核对原卡") }
            finally { buffer.fill(0) }
        }
        private fun readBounded(input: InputStream, buffer: ByteArray): Int = input.use { source ->
            var size = 0
            while (true) {
                val count = source.read(buffer, size, buffer.size - size)
                if (count < 0) break
                check(count > 0 && size + count <= 4096)
                size += count
            }
            size
        }
        private fun parse(text: String): IndependentCredentialCard {
            val values = mutableMapOf<String, String>()
            JsonReader(StringReader(text)).use { reader ->
                reader.isLenient = false; reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    check(name in fields && name !in values && reader.peek() == JsonToken.STRING)
                    values[name] = reader.nextString()
                }
                reader.endObject(); check(reader.peek() == JsonToken.END_DOCUMENT)
            }
            check(values.keys == fields && values.values.all { it.isNotBlank() })
            check(values.getValue("format") == "acrylic-independent-capture-card-v1")
            check(values.getValue("purpose") == "仅核验原设备独立采集身份，仍须原签名与可信期限，不授提交、确认或放行权")
            check(values.getValue("deviceKeySha256").matches(Regex("[0-9a-f]{64}")))
            val password = values.getValue("credential"); check(password.matches(Regex("[A-Za-z0-9_-]{32}")))
            val binding = mapOf("userId" to values.getValue("operatorId"), "deviceId" to values.getValue("deviceId"),
                "warehouseId" to values.getValue("warehouseId"), "deviceKeySha256" to values.getValue("deviceKeySha256"))
            return IndependentCredentialCard(binding, password.toCharArray())
        }
    }
}
