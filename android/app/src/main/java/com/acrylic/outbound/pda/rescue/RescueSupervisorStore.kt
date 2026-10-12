package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.security.TimeSample
import com.acrylic.outbound.pda.security.TrustedTime
import org.json.JSONObject
import java.util.UUID

data class RescueCredentialChoice(val id: String, val identity: RescueReadIdentity, val label: String)

/** The online-provisioned authority and anchor are retained without retaining a login session. */
internal class RescueSupervisorStore(private val storage: RescueCredentialStorage,
    private val sample: () -> TimeSample, private val deviceKey: () -> Pair<String, String>) {
    private val observedRevocations = mutableSetOf<String>()
    data class Provision(val token: String, val trustedKey: JSONObject, val session: JSONObject)

    @Synchronized fun provision(input: Provision, password: CharArray): String {
        val verified = RescueDelegation.verify(input.token, input.trustedKey, deviceKey())
        requireActor(verified, input.session)
        verified.validAt(TrustedTime.now(input.session.getJSONObject("timeAnchor"), sample()))
        val root = storage.read() ?: JSONObject().put("entries", JSONObject())
        val entries = root.getJSONObject("entries"); val previous = entries.optJSONObject(verified.grantId)
        check(verified.grantId !in observedRevocations) { "原授权已撤销，不能重新启用" }
        check(previous == null || (previous.getString("authorization") == input.token && !revoked(previous))) {
            "原授权已撤销或原件不一致，不能重新启用"
        }
        check(previous != null || entries.length() < 20) { "本机主管救援授权超过上限" }
        val entry = JSONObject().put("credentialId", UUID.randomUUID().toString()).put("authorization", input.token)
            .put("trustedKey", JSONObject(input.trustedKey.toString())).put("timeAnchor", JSONObject(input.session.getJSONObject("timeAnchor").toString()))
            .put("password", RescuePassword.create(password)).put("locallyRevoked", false).put("failures", 0)
        entries.put(verified.grantId, entry); storage.write(root)
        return verified.grantId
    }

    @Synchronized fun unlock(id: String, password: CharArray): RescueReadGrant {
        val root = checkNotNull(storage.read()) { "主管救援凭据尚未预置" }
        val entry = root.getJSONObject("entries").getJSONObject(id)
        checkPassword(root, entry, password)
        val verified = active(entry); val credential = entry.getString("credentialId")
        return RescueReadGrant(verified.identity, targets = verified.targets) { validate(id, credential) }.also { it.recheck() }
    }

    @Synchronized fun importRevocation(token: String): String {
        val root = checkNotNull(storage.read()) { "请先预置原主管授权" }
        val entries = root.getJSONObject("entries")
        val id = entries.keys().asSequence().firstOrNull { id ->
            val entry = entries.getJSONObject(id)
            runCatching { RescueDelegation.verifyRevocation(token, entry.getJSONObject("trustedKey"), verified(entry)) }.isSuccess
        } ?: error("撤销签名或原授权范围不匹配")
        val entry = entries.getJSONObject(id)
        check(!entry.has("revocation") || entry.getString("revocation") == token) { "已保留原撤销凭据，不替换原件" }
        observedRevocations.add(id)
        entry.put("revocation", token); storage.write(root)
        return id
    }

    @Synchronized fun revokeLocal(id: String) {
        observedRevocations.add(id)
        val root = checkNotNull(storage.read()); root.getJSONObject("entries").getJSONObject(id).put("locallyRevoked", true)
        storage.write(root)
    }

    @Synchronized fun choices(): List<RescueCredentialChoice> {
        val entries = storage.read()?.getJSONObject("entries") ?: return emptyList()
        return entries.keys().asSequence().map { entries.getJSONObject(it) }.filter { !revoked(it) }.map { entry ->
            val verified = verified(entry)
            RescueCredentialChoice("supervisor:${verified.grantId}", verified.identity,
                "主管只读 · ${verified.identity.operatorId} · ${verified.targets.size}单")
        }.toList()
    }

    private fun requireActor(verified: RescueDelegation.Verified, actor: JSONObject) {
        check(actor.getString("userId") == verified.rescuerId && actor.getString("deviceId") == verified.identity.deviceId &&
            actor.getString("warehouseId") == verified.identity.warehouseId) { "请由指定救援人在原设备/仓库联网预置" }
    }

    private fun checkPassword(root: JSONObject, entry: JSONObject, password: CharArray) {
        val time = sample(); val permitted = RescueAttemptLimit.permitted(entry, time); storage.write(root)
        check(permitted) { "救援核验尝试过多，请保持设备开机15分钟后再试" }
        if (!runCatching { RescuePassword.matches(password, entry.getJSONObject("password")) }.getOrDefault(false)) {
            RescueAttemptLimit.failed(entry, time); storage.write(root); error("主管救援凭据无效或尚未预置")
        }
        RescueAttemptLimit.succeeded(entry); storage.write(root)
    }

    private fun verified(entry: JSONObject) = RescueDelegation.verify(entry.getString("authorization"), entry.getJSONObject("trustedKey"), deviceKey())
    private fun revoked(entry: JSONObject): Boolean {
        if (verified(entry).grantId in observedRevocations) return true
        if (entry.has("revocation")) {
            RescueDelegation.verifyRevocation(entry.getString("revocation"), entry.getJSONObject("trustedKey"), verified(entry))
            return true
        }
        return entry.getBoolean("locallyRevoked")
    }
    private fun active(entry: JSONObject): RescueDelegation.Verified {
        val verified = verified(entry)
        check(!revoked(entry)) { "主管救援授权已撤销，请保留原资料" }
        verified.validAt(runCatching { TrustedTime.now(entry.getJSONObject("timeAnchor"), sample()) }.getOrNull())
        return verified
    }
    @Synchronized private fun validate(id: String, credential: String) {
        val entry = checkNotNull(storage.read()).getJSONObject("entries").getJSONObject(id)
        check(entry.getString("credentialId") == credential) { "主管救援凭据已替换，请重新核验" }
        check(active(entry).grantId == id)
    }
}
