package com.acrylic.outbound.pda.rescue

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import org.json.JSONObject

/** One ephemeral export secret. Its material is never part of the encrypted archive. */
internal class RescuePackageDraft private constructor(private val binding: JSONObject,
    private val secret: CharArray, private val validate: () -> Unit) : AutoCloseable {
    @Volatile private var closed = false
    private var written = false
    var verified = false
        private set

    fun recheck() { check(!closed) { "本次解密材料已关闭，请重新生成" }; validate() }
    fun write(output: () -> OutputStream?, readBack: () -> InputStream?) {
        recheck(); verified = false; written = false
        val bytes = document()
        try {
            checkNotNull(output()).use { it.write(bytes); it.flush() }
            compare(checkNotNull(readBack()), bytes); recheck(); written = true
        } finally { bytes.fill(0) }
    }
    fun verify(input: InputStream) {
        input.use {
            recheck(); verified = false; check(written) { "请先保存并回读独立解密材料" }
            val bytes = document()
            try { compare(it, bytes); recheck(); verified = true } finally { bytes.fill(0) }
        }
    }
    fun credential(): CharArray {
        recheck(); check(verified) { "请重新选择本次解密材料并核对" }; return secret.copyOf()
    }
    override fun close() { closed = true; verified = false; written = false; secret.fill('\u0000') }
    private fun document(): ByteArray {
        recheck()
        return JSONObject(binding.toString()).put("format", "acrylic-rescue-package-secret-v1")
            .put("purpose", "只解密本次应急文件，不是登录、本人救援、主管或独立采集凭据")
            .put("packagePassword", String(secret))
            .put("custody", "敏感解密材料：与加密包及救援凭据卡分开、在设备以外保管。遗失不能恢复该包口令；有有效只读身份时可重新导出。")
            .toString(2).toByteArray(Charsets.UTF_8).also { check(it.size <= 4096) }
    }
    private fun compare(input: InputStream, expected: ByteArray) {
        val buffer = ByteArray(4097)
        try {
            val size = input.use { source ->
                var size = 0
                while (true) {
                    val count = source.read(buffer, size, buffer.size - size)
                    if (count < 0) break
                    check(count > 0 && size + count <= 4096) { "解密材料过大或未完整读取" }
                    size += count
                }
                size
            }
            val actual = buffer.copyOf(size)
            try { check(MessageDigest.isEqual(expected, actual)) { "不是本次保存的原解密材料" } }
            finally { actual.fill(0) }
        } finally { buffer.fill(0) }
    }
    companion object {
        fun create(order: JSONObject, validate: () -> Unit): RescuePackageDraft {
            validate()
            val binding = JSONObject()
            for (field in listOf("source", "localOrderId", "deviceId", "operatorId", "warehouseId")) {
                val value = order.getString(field); check(value.isNotBlank() && value.length <= 128)
                binding.put(field, value)
            }
            binding.put("orderSha256", RescueBundle.sha(order.toString().toByteArray(Charsets.UTF_8)))
            binding.put("materialId", java.util.UUID.randomUUID().toString())
            val entropy = ByteArray(24).also(SecureRandom()::nextBytes)
            try { return RescuePackageDraft(binding, Base64.getUrlEncoder().withoutPadding().encodeToString(entropy).toCharArray(), validate) }
            finally { entropy.fill(0) }
        }
    }
}
