package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.network.SessionIdentity
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import org.json.JSONObject

/** Local password transport for one original signed scope; never a new authority. */
internal class SupervisorRescueSetupDraft private constructor(private val actor: JSONObject,
    private val authorization: RescueDelegation.Verified, private val secret: CharArray) : AutoCloseable {
    @Volatile private var closed = false
    private var written = false
    var verified = false
        private set
    fun write(output: () -> OutputStream?, readBack: () -> InputStream?) {
        requireOpen(); written = false; verified = false
        val bytes = document()
        try {
            checkNotNull(output()).use { it.write(bytes); it.flush() }
            compare(checkNotNull(readBack()), bytes); written = true
        } finally { bytes.fill(0) }
    }
    fun verify(input: InputStream) {
        input.use {
            requireOpen(); verified = false; check(written)
            val bytes = document()
            try { compare(it, bytes); verified = true } finally { bytes.fill(0) }
        }
    }
    fun credential(session: JSONObject, digest: String): CharArray {
        requireCurrent(session, digest)
        return secret.copyOf()
    }
    fun requireCurrent(session: JSONObject, digest: String) {
        requireOpen(); check(verified) { "请先保存原卡并重新选择回读" }
        SessionIdentity.requireSame(actor, session)
        check(listOf("userAuthVersion", "deviceVersion").all { actor.optString(it) == session.optString(it) })
        check(digest == authorization.deviceKeySha256) { "原设备登记钥已变化" }
    }
    fun expectedSession() = JSONObject(actor.toString()).put("deviceKeySha256", authorization.deviceKeySha256)
    fun originalToken(): String { requireOpen(); return authorization.originalToken }
    fun summary() = "指定救援人 ${authorization.rescuerId} · 原采集人 ${authorization.identity.operatorId} · ${authorization.targets.size}单只读范围"
    override fun close() { closed = true; secret.fill('\u0000'); written = false; verified = false }
    private fun requireOpen() = check(!closed) { "设置已关闭，请重新开始" }
    private fun document(): ByteArray {
        requireOpen()
        return JSONObject().put("format", "acrylic-supervisor-readonly-rescue-card-v1")
            .put("purpose", "仅核验原主管签名范围的本地解锁口令，不授扫码、提交、确认、放行或删除权")
            .put("grantId", authorization.grantId).put("rescuerId", authorization.rescuerId)
            .put("deviceId", authorization.identity.deviceId).put("operatorId", authorization.identity.operatorId)
            .put("warehouseId", authorization.identity.warehouseId).put("deviceKeySha256", authorization.deviceKeySha256)
            .put("authorizationSha256", RescueBundle.sha(authorization.originalToken.toByteArray(Charsets.UTF_8)))
            .put("credential", String(secret)).put("custody", "敏感主管凭据卡：设备外独立保管，与应急包和解密材料分开。")
            .toString(2).toByteArray(Charsets.UTF_8)
    }
    private fun compare(input: InputStream, expected: ByteArray) {
        val buffer = ByteArray(4097)
        try {
            val size = input.use { source ->
                var total = 0
                while (true) {
                    val n = source.read(buffer, total, buffer.size - total)
                    if (n < 0) break
                    check(n > 0 && total + n <= 4096); total += n
                }
                total
            }
            val actual = buffer.copyOf(size)
            try { check(MessageDigest.isEqual(expected, actual)) { "回读与本次主管凭据卡不一致" } }
            finally { actual.fill(0) }
        } finally { buffer.fill(0) }
    }
    companion object {
        fun create(session: JSONObject, material: String, binding: Pair<String, String>): SupervisorRescueSetupDraft {
            val parsed = RescueSupervisorMaterial.read(material)
            check(parsed.revocation == null) { "撤销原件请沿原撤销导入，不生成新解锁卡" }
            val auth = RescueDelegation.verify(checkNotNull(parsed.authorization), session.getJSONObject("signingKey"), binding)
            check(session.getString("userId") == auth.rescuerId && session.getString("deviceId") == auth.identity.deviceId &&
                session.getString("warehouseId") == auth.identity.warehouseId) { "请由指定救援人在原设备与仓库设置" }
            val actor = JSONObject()
            for (field in listOf("userId", "deviceId", "warehouseId", "serverRecoveryEpoch", "localSessionId"))
                actor.put(field, session.getString(field).also { check(it.isNotBlank()) })
            for (field in listOf("userAuthVersion", "deviceVersion")) actor.put(field, session.optString(field))
            val entropy = ByteArray(24).also(SecureRandom()::nextBytes)
            try { return SupervisorRescueSetupDraft(actor, auth, Base64.getUrlEncoder().withoutPadding().encodeToString(entropy).toCharArray()) }
            finally { entropy.fill(0) }
        }
    }
}
