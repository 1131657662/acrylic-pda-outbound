package com.acrylic.outbound.pda.network

import android.content.Context
import com.acrylic.outbound.pda.security.*
import org.json.JSONObject

class PdaSession(private val context: Context, val settings: ConnectionSettings,
    private val keys: DeviceKeys, private val vault: SessionVault,
    private val recovery: com.acrylic.outbound.pda.sync.RecoveryProtection,
    private val changed: () -> Unit = {}) {
    private val requests = SessionRequests(vault::read)
    fun saved(): JSONObject? = vault.read()
    fun publicKey(): String = keys.publicKey(settings.device.isBlank()).also { settings.verifyKey(it) }
    fun network() = PdaNetwork(settings.server) { envelope ->
        // A damaged login vault must still allow reauthentication; Room retains the recovery barrier.
        recovery.observe(envelope, runCatching { saved()?.optString("serverRecoveryEpoch") }.getOrNull())
    }

    fun login(username: String, password: String) = requests.run {
        check(settings.device.isNotBlank()) { "请先登记并绑定设备" }
        publicKey()
        val challenge = challenge("LOGIN", username)
        val body = JSONObject().put("username", username).put("password", password).put("deviceId", settings.device)
            .put("challengeId", challenge.getString("challengeId")).put("signature", keys.sign(challenge.getString("signedText")))
        val started = TrustedTime.sample(context)
        val envelope = network().request("pda/auth/login", body)
        val session = envelope.getJSONObject("data").put("username", username)
            .put("localSessionId", java.util.UUID.randomUUID().toString())
        check(session.getString("deviceId") == settings.device)
        update(session, envelope, started)
        val key = call("pda/signing-key")
        val current = requireSession().put("signingKey", key)
        save(current)
        changed()
    }

    private fun challenge(purpose: String, binding: String): JSONObject {
        val result = network().request("pda/auth/challenges", JSONObject().put("deviceId", settings.device)
            .put("purpose", purpose).put("bindingSha256", JwtVerifier.sha(binding.toByteArray(Charsets.UTF_8)))).getJSONObject("data")
        val lines = result.getString("signedText").split('\n')
        check(lines.size == 6 && lines[0] == "acrylic-pda-proof-v1" && lines[1] == result.getString("challengeId") &&
            lines[2] == settings.device && lines[3] == purpose && lines[5] == JwtVerifier.sha(binding.toByteArray(Charsets.UTF_8))) { "认证挑战绑定错误" }
        return result
    }

    fun call(path: String, body: JSONObject? = null, epoch: String? = null, raw: ByteArray? = null,
        extraHeaders: Map<String, String> = emptyMap(), expected: JSONObject? = null): JSONObject =
        requests.run(expected ?: requireSession()) { execute(path, body, epoch, raw, extraHeaders) }

    private fun execute(path: String, body: JSONObject?, epoch: String?, raw: ByteArray?,
        extraHeaders: Map<String, String>): JSONObject {
        val session = requireSession()
        val started = TrustedTime.sample(context)
        val requestHeaders = headers(session, epoch) + extraHeaders
        val envelope = try { network().request(path, body, requestHeaders, raw) }
        catch (failure: ApiFailure) {
            try {
                if (failure.code != "AUTH_EXPIRED") throw failure
                refresh(); network().request(path, body, headers(requireSession(), epoch) + extraHeaders, raw)
            } catch (rejected: ApiFailure) {
                if (rejected.code in setOf("AUTH_EXPIRED", "DEVICE_DISABLED", "PERMISSION_DENIED")) {
                    save(requireSession().put("blockedReason", rejected.message))
                }
                throw rejected
            }
        }
        val current = requireSession()
        update(current, envelope, started)
        return envelope.getJSONObject("data")
    }

    private fun refresh() {
        val previous = requireSession(); val token = previous.getString("refreshToken")
        val challenge = challenge("REFRESH", token)
        val started = TrustedTime.sample(context)
        val envelope = network().request("pda/auth/refresh", JSONObject().put("deviceId", settings.device)
            .put("refreshToken", token).put("challengeId", challenge.getString("challengeId"))
            .put("signature", keys.sign(challenge.getString("signedText"))))
        val session = envelope.getJSONObject("data").put("username", previous.getString("username"))
        check(session.getString("deviceId") == previous.getString("deviceId") &&
            session.getString("userId") == previous.getString("userId") &&
            session.getString("warehouseId") == previous.getString("warehouseId")) { "刷新会话身份不匹配" }
        if (previous.has("localSessionId")) session.put("localSessionId", previous.getString("localSessionId"))
        if (previous.has("signingKey")) session.put("signingKey", previous.getJSONObject("signingKey"))
        update(session, envelope, started)
    }

    fun requireSession(): JSONObject = vault.read() ?: error("请先登录")
    fun headers(session: JSONObject = requireSession(), epoch: String? = null): Map<String, String> = buildMap {
        put("Authorization", "Bearer ${session.getString("accessToken")}")
        if (epoch != null) put("X-Server-Recovery-Epoch", epoch)
    }
    fun logout() = requests.run {
        try { call("pda/auth/logout", JSONObject()) } finally { recovery.transaction { vault.clear() }; changed() }
    }
    fun now(): java.time.Instant {
        val saved = requireSession()
        check(!saved.has("blockedReason")) { saved.getString("blockedReason") }
        return TrustedTime.now(saved.getJSONObject("timeAnchor"), TrustedTime.sample(context))
    }
    private fun update(session: JSONObject, envelope: JSONObject, started: TimeSample) {
        session.put("serverRecoveryEpoch", envelope.getString("serverRecoveryEpoch"))
        // Anchor at request start: transmission delay can shorten, never extend, the offline deadline.
        session.put("timeAnchor", TrustedTime.anchor(envelope.getString("serverTime"), started))
        save(session)
    }
    private fun save(session: JSONObject) = recovery.transaction { vault.save(session) }
}
