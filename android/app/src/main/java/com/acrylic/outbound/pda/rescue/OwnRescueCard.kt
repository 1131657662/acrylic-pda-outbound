package com.acrylic.outbound.pda.rescue

import android.util.JsonReader
import android.util.JsonToken
import java.io.InputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Untrusted password transport only. The original local verifier remains the authority. */
internal class OwnRescueCard private constructor(private val identity: RescueReadIdentity,
    private val keyDigest: String, private val secret: CharArray) : AutoCloseable {
    private var closed = false
    fun credential(selected: RescueReadIdentity, digest: String): CharArray {
        check(!closed && identity == selected && digest == keyDigest) { "凭据卡与所选本人、设备或仓库不一致" }
        return secret.copyOf()
    }
    override fun close() { closed = true; secret.fill('\u0000') }

    companion object {
        private val fields = setOf("format", "purpose", "deviceId", "operatorId", "warehouseId",
            "deviceKeySha256", "credential", "custody")
        fun read(input: InputStream): OwnRescueCard {
            val buffer = ByteArray(4097)
            try {
                val size = input.use { source ->
                    var size = 0
                    while (true) {
                        val count = source.read(buffer, size, buffer.size - size)
                        if (count < 0) break
                        check(count > 0 && size + count <= 4096)
                        size += count
                    }
                    size
                }
                val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                return parse(decoder.decode(ByteBuffer.wrap(buffer, 0, size)).toString())
            } catch (_: Exception) { error("凭据卡格式无效或读取未完成，请核对本人原卡") }
            finally { buffer.fill(0) }
        }
        private fun parse(text: String): OwnRescueCard {
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
            check(values.getValue("format") == "acrylic-own-readonly-rescue-card-v1")
            check(values.getValue("purpose") == "仅解锁原设备上本人的只读记录，不授扫码、提交或放行权")
            val digest = values.getValue("deviceKeySha256"); check(digest.matches(Regex("[0-9a-f]{64}")))
            val password = values.getValue("credential"); check(password.matches(Regex("[A-Za-z0-9_-]{32}")))
            val identity = RescueReadIdentity(values.getValue("deviceId"), values.getValue("operatorId"), values.getValue("warehouseId"))
            return OwnRescueCard(identity, digest, password.toCharArray())
        }
    }
}
