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
    fun provisionOwn(password: CharArray) {
        try { provision(password) } finally { password.fill('\u0000') }
    }
    private fun provision(password: CharArray) {
        val original = session.requireSession()
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
    companion object {
        private val instances = java.util.WeakHashMap<Context, RescueCredentials>()
        @Synchronized fun shared(context: Context, session: PdaSession): RescueCredentials =
            instances.getOrPut(context.applicationContext) { RescueCredentials(context.applicationContext, session) }
    }
}
