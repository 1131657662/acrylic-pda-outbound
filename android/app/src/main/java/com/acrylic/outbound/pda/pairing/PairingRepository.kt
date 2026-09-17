package com.acrylic.outbound.pda.pairing

import android.os.Build
import com.acrylic.outbound.pda.network.ConnectionSettings
import com.acrylic.outbound.pda.network.PdaNetwork
import com.acrylic.outbound.pda.security.DeviceKeys
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

class PairingRepository(private val vault: PairingVault, private val settings: ConnectionSettings, private val keys: DeviceKeys) {
    @Synchronized fun saved(): JSONObject? = vault.read()
    @Synchronized fun start(server: String): JSONObject {
        check(settings.device.isBlank()) { "本机已有设备身份，请使用原身份登录" }
        val previous = vault.read()
        check(previous == null || previous.optJSONObject("result")?.optString("status") in setOf("EXPIRED", "CANCELLED")) { "请先恢复原登记申请" }
        val address = ConnectionSettings.validateServer(server)
        val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val payload = JSONObject().put("applicationId", UUID.randomUUID().toString())
            .put("credential", Base64.getUrlEncoder().withoutPadding().encodeToString(secret)).put("publicKey", keys.publicKey(previous == null))
            .put("model", "${Build.MANUFACTURER} ${Build.MODEL}".replace('\n', ' ').replace('\r', ' ').take(100)).put("approvalSha256", "")
        val state = JSONObject().put("server", address).put("payload", payload).put("operation", "CREATE")
        vault.save(state); return resume()
    }
    @Synchronized fun resume(): JSONObject {
        val state = checkNotNull(vault.read()) { "请先开始登记" }
        check(keys.publicKey(false) == state.getJSONObject("payload").getString("publicKey")) { "原申请私钥已变化，请保留资料" }
        val action = state.optString("operation").ifBlank { "STATUS" }
        val response = send(state, action); PairingVerification.result(state, response)
        state.put("result", response).put("operation", ""); vault.save(state)
        if (response.getString("status") == "COMPLETED") install(state)
        return state
    }
    @Synchronized fun confirm(): JSONObject {
        val state = checkNotNull(vault.read()); val result = state.getJSONObject("result")
        check(state.optString("operation").isEmpty() && result.getString("status") == "PENDING") { "请先核对原登记状态" }
        state.getJSONObject("payload").put("approvalSha256", result.getString("approvalSha256"))
        state.put("operation", "CONFIRM"); vault.save(state); return resume()
    }
    @Synchronized fun cancel(): JSONObject {
        val state = checkNotNull(vault.read())
        check(state.optString("operation").isEmpty()) { "原操作结果待核对，请先恢复，不能直接取消" }
        check(state.getJSONObject("result").getString("status") in setOf("WAITING", "PENDING"))
        state.put("operation", "CANCEL"); vault.save(state); return resume()
    }
    private fun send(state: JSONObject, action: String): JSONObject {
        val network = PdaNetwork(state.getString("server")); val p = state.getJSONObject("payload")
        val challenge = network.request("pda/pairing/challenges", JSONObject().put("applicationId", p.getString("applicationId"))
            .put("publicKey", p.getString("publicKey")).put("purpose", action).put("bindingSha256", PairingVerification.sha(PairingVerification.binding(p))))
        val epoch = challenge.getString("serverRecoveryEpoch")
        check(!state.has("epoch") || state.getString("epoch") == epoch) { "服务恢复代次已变化，请保留申请并联系管理员" }
        state.put("epoch", epoch); vault.save(state)
        val proof = PairingVerification.challenge(p, action, challenge.getJSONObject("data"), epoch)
        val body = JSONObject().put("payload", p).put("challengeId", challenge.getJSONObject("data").getString("challengeId")).put("signature", keys.sign(proof))
        val path = if (action == "CREATE") "applications" else action.lowercase(java.util.Locale.ROOT)
        val envelope = network.request("pda/pairing/$path", body)
        check(envelope.getString("serverRecoveryEpoch") == epoch) { "服务器恢复代次已变化" }
        return envelope.getJSONObject("data")
    }
    private fun install(state: JSONObject) {
        val p = state.getJSONObject("payload"); val result = state.getJSONObject("result")
        PairingVerification.result(state, result)
        settings.bind(state.getString("server"), result.getString("deviceId"), p.getString("publicKey"))
        check(settings.device == result.getString("deviceId")) { "本机身份尚未保存，请恢复原登记结果" }
    }
}
