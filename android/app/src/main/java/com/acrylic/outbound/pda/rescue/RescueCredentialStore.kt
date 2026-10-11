package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.security.TimeSample
import org.json.JSONObject
import java.util.UUID

/** Storage is authenticated/encrypted by the Android vault; test stores are synthetic. */
internal interface RescueCredentialStorage { fun read(): JSONObject?; fun write(state: JSONObject) }

internal class RescueCredentialStore(private val storage: RescueCredentialStorage,
    private val sample: () -> TimeSample, private val deviceKey: () -> Pair<String, String>) {
    @Synchronized fun provision(identity: RescueReadIdentity, password: CharArray) {
        val (device, keyDigest) = deviceKey()
        check(device == identity.deviceId)
        val root = storage.read() ?: JSONObject().put("entries", JSONObject())
        val entries = root.getJSONObject("entries")
        check(entries.has(identity.operatorId) || entries.length() < 20) { "本机救援身份超过上限" }
        val entry = JSONObject().put("credentialId", UUID.randomUUID().toString()).put("deviceId", device)
            .put("operatorId", identity.operatorId).put("warehouseId", identity.warehouseId).put("keyDigest", keyDigest)
            .put("password", RescuePassword.create(password)).put("purpose", "OWN_READ_ONLY_RESCUE")
            .put("failures", 0).put("locallyRevoked", false)
        entries.put(identity.operatorId, entry); storage.write(root)
    }
    @Synchronized fun unlock(operator: String, password: CharArray): RescueReadGrant {
        val root = checkNotNull(storage.read()) { "救援凭据无效或尚未预置" }
        val entry = checkNotNull(root.getJSONObject("entries").optJSONObject(operator)) { "救援凭据无效或尚未预置" }
        val time = sample()
        val allowed = RescueAttemptLimit.permitted(entry, time)
        storage.write(root)
        check(allowed) { "救援核验尝试过多，请保持设备开机15分钟后再试" }
        val correct = runCatching { RescuePassword.matches(password, entry.getJSONObject("password")) }.getOrDefault(false)
        if (!correct) { RescueAttemptLimit.failed(entry, time); storage.write(root); error("救援凭据无效或尚未预置") }
        RescueAttemptLimit.succeeded(entry); storage.write(root)
        val identity = RescueReadIdentity(entry.getString("deviceId"), entry.getString("operatorId"), entry.getString("warehouseId"))
        val credential = entry.getString("credentialId")
        return RescueReadGrant(identity) { validate(operator, credential) }.also { it.recheck() }
    }
    @Synchronized fun revoke(operator: String) {
        val root = storage.read() ?: return
        root.getJSONObject("entries").optJSONObject(operator)?.put("locallyRevoked", true)
        storage.write(root)
    }
    @Synchronized fun identities(): List<RescueReadIdentity> {
        val entries = storage.read()?.getJSONObject("entries") ?: return emptyList()
        return entries.keys().asSequence().map { entries.getJSONObject(it) }.filter { !it.getBoolean("locallyRevoked") }
            .map { RescueReadIdentity(it.getString("deviceId"), it.getString("operatorId"), it.getString("warehouseId")) }.toList()
    }
    @Synchronized private fun validate(operator: String, credential: String) {
        val entry = checkNotNull(storage.read()).getJSONObject("entries").getJSONObject(operator)
        val (device, keyDigest) = deviceKey()
        check(!entry.getBoolean("locallyRevoked") && entry.getString("credentialId") == credential &&
            entry.getString("deviceId") == device && entry.getString("keyDigest") == keyDigest) { "救援凭据已失效，请保留本机资料" }
    }
}
