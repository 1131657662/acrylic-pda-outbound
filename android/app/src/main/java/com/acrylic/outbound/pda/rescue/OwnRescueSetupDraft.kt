package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.network.SessionIdentity
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import org.json.JSONObject

/** Ephemeral setup only. No URI, password, document or login token is stored in application files. */
internal class OwnRescueSetupDraft private constructor(private val identity: JSONObject,
    private val keyDigest: String, private val secret: CharArray) : AutoCloseable {
    @Volatile private var closed = false
    private var written = false
    var verified = false
        private set

    fun write(output: () -> OutputStream?, readBack: () -> InputStream?) {
        requireOpen(); verified = false; written = false
        val bytes = document()
        try {
            checkNotNull(output()) { "凭据卡目标不可写" }.use { it.write(bytes); it.flush() }
            compare(checkNotNull(readBack()) { "凭据卡目标无法回读" }, bytes)
            written = true
        } finally { bytes.fill(0) }
    }

    fun verify(input: InputStream) {
        input.use {
            requireOpen(); verified = false
            check(written) { "请先保存并回读凭据卡" }
            val bytes = document()
            try { compare(it, bytes); verified = true } finally { bytes.fill(0) }
        }
    }

    fun credential(session: JSONObject, digest: String): CharArray {
        requireOpen(); check(verified) { "请重新选取已独立保管的凭据卡并回读" }
        SessionIdentity.requireSame(identity, session)
        check(digest == keyDigest) { "原设备登记钥已变化，请重新开始设置" }
        return secret.copyOf()
    }

    fun expectedSession(): JSONObject { requireOpen(); return JSONObject(identity.toString()) }
    override fun close() { secret.fill('\u0000'); closed = true; verified = false; written = false }
    private fun requireOpen() = check(!closed) { "设置已结束，请重新生成凭据" }

    private fun document(): ByteArray {
        requireOpen()
        return JSONObject().put("format", "acrylic-own-readonly-rescue-card-v1")
            .put("purpose", "仅解锁原设备上本人的只读记录，不授扫码、提交或放行权")
            .put("deviceId", identity.getString("deviceId")).put("operatorId", identity.getString("userId"))
            .put("warehouseId", identity.getString("warehouseId")).put("deviceKeySha256", keyDigest)
            .put("credential", String(secret))
            .put("custody", "敏感凭据卡：移至设备以外，与应急包/文件解密口令分开保管，不发送普通群聊。")
            .toString(2).toByteArray(Charsets.UTF_8)
    }

    private fun compare(input: InputStream, expected: ByteArray) {
        val buffer = ByteArray(4097)
        try {
            val size = input.use { source ->
                var size = 0
                while (true) {
                    val n = source.read(buffer, size, buffer.size - size)
                    if (n < 0) break
                    check(n > 0 && size + n <= 4096) { "凭据卡过大或读取未完成" }
                    size += n
                }
                size
            }
            val actual = buffer.copyOf(size)
            try { check(MessageDigest.isEqual(expected, actual)) { "所选凭据卡与本次生成原件不一致" } }
            finally { actual.fill(0) }
        } finally { buffer.fill(0) }
    }

    companion object {
        fun create(session: JSONObject, keyDigest: String): OwnRescueSetupDraft {
            val identity = JSONObject()
            for (field in listOf("userId", "deviceId", "warehouseId", "serverRecoveryEpoch", "localSessionId")) {
                val value = session.getString(field); check(value.isNotBlank()) { "请先联网登录原人员" }
                identity.put(field, value)
            }
            check(keyDigest.matches(Regex("[0-9a-f]{64}")))
            val entropy = ByteArray(24).also(SecureRandom()::nextBytes)
            try { return OwnRescueSetupDraft(identity, keyDigest, Base64.getUrlEncoder().withoutPadding().encodeToString(entropy).toCharArray()) }
            finally { entropy.fill(0) }
        }
    }
}
