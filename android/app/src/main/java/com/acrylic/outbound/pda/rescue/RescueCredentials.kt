package com.acrylic.outbound.pda.rescue

import android.content.Context
import com.acrylic.outbound.pda.network.PdaSession
import com.acrylic.outbound.pda.network.SessionIdentity
import com.acrylic.outbound.pda.security.DeviceKeys
import com.acrylic.outbound.pda.security.TrustedTime
import java.util.Base64

/** Own credentials are pre-provisioned after online authentication; identity is never typed by a rescue user. */
class RescueCredentials(context: Context, private val session: PdaSession) {
    private val keys = DeviceKeys()
    private val store = RescueCredentialStore(RescueCredentialVault(context), { TrustedTime.sample(context) }) {
        val public = keys.publicKey(false).also(session.settings::verifyKey)
        session.settings.device to RescueBundle.sha(Base64.getDecoder().decode(public))
    }
    private val supervisor = RescueSupervisorStore(RescueCredentialVault(context, supervisor = true),
        { TrustedTime.sample(context) }) { binding() }
    private fun binding(): Pair<String, String> {
        val public = keys.publicKey(false).also(session.settings::verifyKey)
        return session.settings.device to RescueBundle.sha(Base64.getDecoder().decode(public))
    }
    fun provisionOwn(password: CharArray, expected: org.json.JSONObject? = null) {
        try { provision(password, expected) } finally { password.fill('\u0000') }
    }
    private fun provision(password: CharArray, expected: org.json.JSONObject?) {
        val original = session.requireSession()
        if (expected != null) SessionIdentity.requireSame(expected, original)
        session.call("pda/devices/me", expected = original)
        SessionIdentity.requireSame(original, session.saved())
        val identity = RescueReadIdentity(original.getString("deviceId"), original.getString("userId"), original.getString("warehouseId"))
        store.provision(identity, password)
        try { SessionIdentity.requireSame(original, session.saved()) }
        catch (error: Exception) { store.revoke(identity.operatorId); throw error }
    }
    fun unlock(operatorId: String, password: CharArray): RescueReadGrant =
        try { store.unlock(operatorId, password) } finally { password.fill('\u0000') }
    fun revokeOwn() { store.revoke(session.requireSession().getString("userId")) }
    fun identities(): List<RescueReadIdentity> = store.identities()
    fun choices(): List<RescueCredentialChoice> = identities().map {
        RescueCredentialChoice("own:${it.operatorId}", it, "本人只读 · ${it.operatorId}")
    } + supervisor.choices()
    fun unlockChoice(id: String, password: CharArray): RescueReadGrant = try {
        when {
            id.startsWith("own:") -> store.unlock(id.removePrefix("own:"), password)
            id.startsWith("supervisor:") -> supervisor.unlock(id.removePrefix("supervisor:"), password)
            else -> error("请选择已预置的只读凭据")
        }
    } finally { password.fill('\u0000') }
    internal fun unlockSupervisorCard(id: String, card: SupervisorRescueCard): RescueReadGrant {
        check(id.startsWith("supervisor:")) { "请选择已预置的主管只读授权" }
        return supervisor.unlockCard(id.removePrefix("supervisor:"), card)
    }
    fun importSupervisor(text: String, password: CharArray): String = try {
        val material = RescueSupervisorMaterial.read(text)
        // Revocations never need a live login and can only reduce access.
        if (material.revocation != null) {
            supervisor.importRevocation(material.revocation)
            "主管撤销签名已核验并保存；原资料保留"
        } else provisionSupervisor(checkNotNull(material.authorization), password, null)
    } finally { password.fill('\u0000') }
    internal fun importSupervisorSetup(draft: SupervisorRescueSetupDraft): String {
        val password = draft.credential(session.requireSession(), binding().second)
        try { return provisionSupervisor(draft.originalToken(), password, draft) }
        finally { password.fill('\u0000') }
    }
    private fun provisionSupervisor(token: String, password: CharArray, expected: SupervisorRescueSetupDraft?): String {
        val original = session.requireSession()
        expected?.requireCurrent(original, binding().second)
        val verified = RescueDelegation.verify(token, original.getJSONObject("signingKey"), binding())
        val material = session.call("pda/rescue-grants/${verified.grantId}", expected = original)
        SessionIdentity.requireSame(original, session.saved())
        check(material.getJSONObject("authorization").getString("signedAuthorization") == token) { "服务端原授权与导入原件不一致" }
        if (!material.isNull("revocation")) {
            supervisor.importRevocation(material.getJSONObject("revocation").getString("signedRevocation"))
            error("主管授权已撤销；撤销原件已保存，原资料保留")
        }
        check(material.getString("status") == "AUTHORIZED" && material.isNull("revocation")) { "主管授权已撤销或到期，请保留原资料" }
        val trusted = session.requireSession()
        expected?.requireCurrent(trusted, binding().second)
        val id = supervisor.provision(RescueSupervisorStore.Provision(token, trusted.getJSONObject("signingKey"), trusted), password)
        try {
            SessionIdentity.requireSame(original, session.saved())
            expected?.requireCurrent(checkNotNull(session.saved()), binding().second)
        } catch (error: Exception) { supervisor.revokeLocal(id); throw error }
        return "主管签名只读范围已预置；退出登录后可凭本地口令取回原件"
    }
    companion object {
        private val instances = java.util.WeakHashMap<Context, RescueCredentials>()
        @Synchronized fun shared(context: Context, session: PdaSession): RescueCredentials =
            instances.getOrPut(context.applicationContext) { RescueCredentials(context.applicationContext, session) }
    }
}
