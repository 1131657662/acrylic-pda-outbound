package com.acrylic.outbound.pda.network

import android.content.Context
import com.acrylic.outbound.pda.security.*
import org.json.JSONObject

class PdaSession(private val context: Context, val settings: ConnectionSettings,
    private val keys: DeviceKeys, private val vault: SessionVault) {
    fun saved(): JSONObject? = vault.read()
    fun publicKey(): String = keys.publicKey(settings.device.isBlank()).also { settings.verifyKey(it) }
    fun network() = PdaNetwork(settings.server)

    fun login(username: String, password: String) {
        check(settings.device.isNotBlank()) { "请先登记并绑定设备" }
        publicKey()
        val challenge = challenge("LOGIN", username)
        val body = JSONObject().put("username", username).put("password", password).put("deviceId", settings.device)
            .put("challengeId", challenge.getString("challengeId")).put("signature", keys.sign(challenge.getString("signedText")))
        val started = TrustedTime.sample(context)
        val envelope = network().request("pda/auth/login", body)
        val session = envelope.getJSONObject("data").put("username", username)
        check(session.getString("deviceId") == settings.device)
        update(session, envelope, started)
        val key = call("pda/signing-key")
        val current = requireSession().put("signingKey", key)
        vault.save(current)
    }

    private fun challenge(purpose: String, binding: String): JSONObject {
        val result = network().request("pda/auth/challenges", JSONObject().put("deviceId", settings.device)
            .put("purpose", purpose).put("bindingSha256", JwtVerifier.sha(binding.toByteArray(Charsets.UTF_8)))).getJSONObject("data")
        val lines = result.getString("signedText").split('\n')
        check(lines.size == 6 && lines[0] == "acrylic-pda-proof-v1" && lines[1] == result.getString("challengeId") &&
            lines[2] == settings.device && lines[3] == purpose && lines[5] == JwtVerifier.sha(binding.toByteArray(Charsets.UTF_8))) { "认证挑战绑定错误" }
        return result
    }

    fun call(path: String, body: JSONObject? = null, epoch: String? = null, raw: ByteArray? = null): JSONObject {
        val session = requireSession()
        val started = TrustedTime.sample(context)
        val envelope = try { network().request(path, body, headers(session, epoch), raw) }
        catch (failure: ApiFailure) {
            try {
                if (failure.code != "AUTH_EXPIRED") throw failure
                refresh(); network().request(path, body, headers(requireSession(), epoch), raw)
            } catch (rejected: ApiFailure) {
                if (rejected.code in setOf("AUTH_EXPIRED", "DEVICE_DISABLED", "PERMISSION_DENIED")) {
                    vault.save(requireSession().put("blockedReason", rejected.message))
                }
                throw rejected
            }
        }
        val current = requireSession()
        if (current.getString("serverRecoveryEpoch") != envelope.getString("serverRecoveryEpoch")) {
            update(current, envelope, started); error("服务器恢复代次已变化，请保留资料并联系主管对账")
        }
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
        if (previous.has("signingKey")) session.put("signingKey", previous.getJSONObject("signingKey"))
        update(session, envelope, started)
    }

    fun requireSession(): JSONObject = vault.read() ?: error("请先登录")
    fun headers(session: JSONObject = requireSession(), epoch: String? = null): Map<String, String> = buildMap {
        put("Authorization", "Bearer ${session.getString("accessToken")}")
        if (epoch != null) put("X-Server-Recovery-Epoch", epoch)
    }
    fun logout() {
        try { call("pda/auth/logout", JSONObject()) } finally { vault.clear() }
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
        vault.save(session)
    }
}
