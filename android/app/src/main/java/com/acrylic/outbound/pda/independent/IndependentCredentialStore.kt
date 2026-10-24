package com.acrylic.outbound.pda.independent

import com.acrylic.outbound.pda.rescue.RescueAttemptLimit
import com.acrylic.outbound.pda.rescue.RescuePassword
import com.acrylic.outbound.pda.security.TrustedTime
import org.json.JSONObject
import java.util.UUID

internal interface IndependentCredentialStorage { fun read(): JSONObject?; fun write(state: JSONObject) }
internal data class IndependentCredentialIntent(val key: String, val identity: JSONObject) {
    val body get() = JSONObject().put("deviceId", identity.getString("deviceId"))
        .put("deviceKeySha256", identity.getString("deviceKeySha256"))
}
internal data class IndependentCredentialOpen(val requestKey: String, val verifierId: String)
internal data class IndependentCredentialIncoming(val intent: IndependentCredentialIntent, val receipt: JSONObject,
    val context: IndependentCredentialContext)

/** Context is sampled outside this monitor: no Room or session network locks under the vault lock. */
internal class IndependentCredentialStore(private val storage: IndependentCredentialStorage) {
    @Synchronized fun prepare(context: IndependentCredentialContext): IndependentCredentialIntent {
        val identity = context.intent(); val root = root(); val entries = root.getJSONObject("entries")
        val pending = entries.keys().asSequence().map { it to entries.getJSONObject(it) }.firstOrNull {
            !it.second.has("receipt") && it.second.getJSONObject("identity").toString() == identity.toString()
        }
        if (pending != null) return IndependentCredentialIntent(pending.first, pending.second.getJSONObject("identity"))
        check(entries.length() < 20) { "本机独立身份原件已达20份；已有原件保留" }
        val key = UUID.randomUUID().toString()
        entries.put(key, JSONObject().put("identity", identity).put("locallyRevoked", false))
        storage.write(root)
        return IndependentCredentialIntent(key, checkNotNull(storage.read()).getJSONObject("entries").getJSONObject(key).getJSONObject("identity"))
    }
    @Synchronized fun receive(incoming: IndependentCredentialIncoming, password: CharArray): IndependentCredentialProof {
        val (intent, receipt, context) = incoming
        context.requireIntent(intent.identity)
        val trusted = context.session.getJSONObject("signingKey")
        val proof = IndependentCredentialProof.verify(receipt.getString("signedCredential"), trusted)
        context.requireProof(proof); proof.at(TrustedTime.now(context.session.getJSONObject("timeAnchor"), context.sample))
        check(receipt.getString("credentialId") == proof.id && receipt.getString("issuedAt") == proof.issued.toString() &&
            receipt.getString("expiresAt") == proof.expires.toString()) { "服务端原件与签名凭据不一致" }
        val root = root(); val entry = root.getJSONObject("entries").getJSONObject(intent.key)
        check(entry.getJSONObject("identity").toString() == intent.identity.toString())
        if (entry.has("receipt")) { check(entry.getString("receipt") == receipt.toString()); return proof }
        entry.put("receipt", receipt.toString()).put("trustedKey", JSONObject(trusted.toString()))
            .put("timeAnchor", JSONObject(context.session.getJSONObject("timeAnchor").toString()))
            .put("password", RescuePassword.create(password)).put("verifierId", UUID.randomUUID().toString()).put("failures", 0)
        root.put("active", intent.key); storage.write(root)
        check(checkNotNull(storage.read()).getJSONObject("entries").getJSONObject(intent.key).getString("receipt") == receipt.toString())
        return proof
    }
    @Synchronized fun unlock(password: CharArray, context: IndependentCredentialContext): IndependentCredentialOpen {
        val root = root(); val key = root.optString("active"); val entry = root.getJSONObject("entries").optJSONObject(key)
        check(entry != null) { "尚未预置独立采集凭据" }
        val allowed = RescueAttemptLimit.permitted(entry, context.sample); storage.write(root)
        check(allowed) { "独立身份核验尝试过多，请保持设备开机15分钟后再试" }
        if (!runCatching { RescuePassword.matches(password, entry.getJSONObject("password")) }.getOrDefault(false)) {
            RescueAttemptLimit.failed(entry, context.sample); storage.write(root); error("独立采集凭据无效")
        }
        RescueAttemptLimit.succeeded(entry); storage.write(root)
        return IndependentCredentialOpen(key, entry.getString("verifierId")).also { validate(it, context) }
    }
    @Synchronized fun validate(open: IndependentCredentialOpen, context: IndependentCredentialContext): IndependentCredentialProof {
        val root = root(); val entry = root.getJSONObject("entries").getJSONObject(open.requestKey)
        check(root.optString("active") == open.requestKey && !entry.getBoolean("locallyRevoked") &&
            entry.getString("verifierId") == open.verifierId) { "本机独立采集资格已失效；原件保留" }
        context.requireIntent(entry.getJSONObject("identity"))
        val proof = IndependentCredentialProof.verify(JSONObject(entry.getString("receipt")).getString("signedCredential"), entry.getJSONObject("trustedKey"))
        context.requireProof(proof)
        proof.at(TrustedTime.now(entry.getJSONObject("timeAnchor"), context.sample))
        return proof
    }
    @Synchronized fun revoke() {
        val root = root(); root.getJSONObject("entries").optJSONObject(root.optString("active"))?.put("locallyRevoked", true)
        storage.write(root)
    }
    @Synchronized fun snapshot(open: IndependentCredentialOpen, context: IndependentCredentialContext): IndependentCredentialSnapshot {
        val proof = validate(open, context)
        val entry = root().getJSONObject("entries").getJSONObject(open.requestKey)
        return IndependentCredentialSnapshot(proof, entry.getJSONObject("trustedKey").toString(),
            entry.getJSONObject("timeAnchor").toString(), entry.getJSONObject("identity").getString("localSessionId"),
            JSONObject(entry.getString("receipt")).let {
                if (it.has("operatorNameProof") && !it.isNull("operatorNameProof")) it.getString("operatorNameProof") else null
            })
    }
    @Synchronized fun status(): String {
        val root = root(); val entries = root.getJSONObject("entries"); val entry = entries.optJSONObject(root.optString("active"))
        val pending = entries.keys().asSequence().count { !entries.getJSONObject(it).has("receipt") }
        if (entry == null) return "尚无已预置独立身份；未确认原请求 $pending 份"
        val receipt = JSONObject(entry.getString("receipt"))
        return "原凭据 ${receipt.getString("credentialId")}\n签名期限 ${receipt.getString("expiresAt")}\n" +
            "${if (entry.getBoolean("locallyRevoked")) "本机已撤销新增采集" else "新增采集仍须核验口令和可信时间"}；未确认原请求 $pending 份"
    }
    private fun root() = storage.read() ?: JSONObject().put("entries", JSONObject())
}
