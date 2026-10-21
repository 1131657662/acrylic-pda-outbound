package com.acrylic.outbound.pda.rescue

import android.util.JsonReader
import android.util.JsonToken
import java.io.InputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Password transport only; the selected original vault authorization remains the authority. */
internal class SupervisorRescueCard private constructor(private val binding: Map<String, String>,
    private val secret: CharArray) : AutoCloseable {
    private var closed = false
    fun credential(original: RescueDelegation.Verified): CharArray {
        val expected = mapOf("grantId" to original.grantId, "rescuerId" to original.rescuerId,
            "deviceId" to original.identity.deviceId, "operatorId" to original.identity.operatorId,
            "warehouseId" to original.identity.warehouseId, "deviceKeySha256" to original.deviceKeySha256,
            "authorizationSha256" to RescueBundle.sha(original.originalToken.toByteArray(Charsets.UTF_8)))
        check(!closed && binding == expected) { "主管凭据卡与所选原授权或人机仓钥不一致" }
        return secret.copyOf()
    }
    override fun close() { closed = true; secret.fill('\u0000') }

    companion object {
        private val fields = setOf("format", "purpose", "grantId", "rescuerId", "deviceId", "operatorId",
            "warehouseId", "deviceKeySha256", "authorizationSha256", "credential", "custody")
        fun read(input: InputStream): SupervisorRescueCard {
            val buffer = ByteArray(4097)
            try {
                val size = readBounded(input, buffer)
                val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                return parse(decoder.decode(ByteBuffer.wrap(buffer, 0, size)).toString())
            } catch (_: Exception) { error("主管凭据卡格式无效或读取未完成，请核对原卡") }
            finally { buffer.fill(0) }
        }
        private fun readBounded(input: InputStream, buffer: ByteArray): Int = input.use { source ->
            var size = 0
            while (true) {
                val count = source.read(buffer, size, buffer.size - size)
                if (count < 0) break
                check(count > 0 && size + count <= 4096); size += count
            }
            size
        }
        private fun parse(text: String): SupervisorRescueCard {
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
            check(values.getValue("format") == "acrylic-supervisor-readonly-rescue-card-v1")
            check(values.getValue("purpose") == "仅核验原主管签名范围的本地解锁口令，不授扫码、提交、确认、放行或删除权")
            for (field in listOf("deviceKeySha256", "authorizationSha256"))
                check(values.getValue(field).matches(Regex("[0-9a-f]{64}")))
            val password = values.getValue("credential"); check(password.matches(Regex("[A-Za-z0-9_-]{32}")))
            return SupervisorRescueCard(values.filterKeys { it !in setOf("format", "purpose", "credential", "custody") }, password.toCharArray())
        }
    }
}
