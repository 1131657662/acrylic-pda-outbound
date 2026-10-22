package com.acrylic.outbound.pda.independent

import android.content.Context
import com.acrylic.outbound.pda.network.PdaSession
import com.acrylic.outbound.pda.network.SessionIdentity
import com.acrylic.outbound.pda.network.ApiFailure
import com.acrylic.outbound.pda.security.*
import com.acrylic.outbound.pda.sync.RecoveryProtection
import java.util.Base64
import org.json.JSONObject

internal class IndependentCaptureGrant(val proof: IndependentCredentialProof, private val check: () -> IndependentCredentialSnapshot) {
    fun recheck(): IndependentCredentialProof = check().proof
    fun snapshot(): IndependentCredentialSnapshot = check()
}

class IndependentCredentials private constructor(private val context: Context, private val session: PdaSession,
    private val recovery: RecoveryProtection) {
    private val store = IndependentCredentialStore(IndependentCredentialVault(context))
    private fun environment(): IndependentCredentialContext {
        recovery.requireNormal()
        val public = DeviceKeys().publicKey(false).also(session.settings::verifyKey)
        return IndependentCredentialContext(session.requireSession(), session.settings.device to
            JwtVerifier.sha(Base64.getDecoder().decode(public)), TrustedTime.sample(context))
    }
    fun provision(password: CharArray, expected: JSONObject? = null): String = try {
        require(password.size in 16..128 && password.all { it.code in 33..126 }) { "独立口令须为16至128位无空格ASCII字符" }
        val before = environment()
        if (expected != null) before.requireIntent(expected)
        val intent = store.prepare(before)
        val receipt = session.call("pda/independent-credentials", intent.body,
            epoch = intent.identity.getString("serverRecoveryEpoch"), extraHeaders = mapOf("Idempotency-Key" to intent.key), expected = before.session)
        val after = environment(); SessionIdentity.requireSame(before.session, after.session)
        val proof = store.receive(IndependentCredentialIncoming(intent, receipt, after), password)
        try { SessionIdentity.requireSame(after.session, session.saved()) }
        catch (error: Exception) { store.revoke(); throw error }
        "已保存签名独立身份；原期限 ${proof.expires}，仍须离线口令核验。"
    } catch (failure: ApiFailure) {
        if (failure.code in setOf("PERMISSION_DENIED", "AUTH_EXPIRED", "DEVICE_DISABLED", "INDEPENDENT_CAPTURE_DISABLED")) store.revoke()
        throw failure
    } finally { password.fill('\u0000') }
    internal fun unlock(password: CharArray): IndependentCaptureGrant = try {
        val open = store.unlock(password, environment())
        val proof = store.validate(open, environment())
        IndependentCaptureGrant(proof) { store.snapshot(open, environment()) }
    } finally { password.fill('\u0000') }
    internal fun cardSelection(): JSONObject {
        val public = DeviceKeys().publicKey(false).also(session.settings::verifyKey)
        return IndependentCredentialContext(session.requireSession(), session.settings.device to
            JwtVerifier.sha(Base64.getDecoder().decode(public)), TrustedTime.sample(context)).intent()
    }
    internal fun unlockCard(input: java.io.InputStream, expected: JSONObject): IndependentCaptureGrant {
        environment().requireIntent(expected)
        val grant = IndependentCredentialCard.read(input).use { unlock(it.credential(expected)) }
        environment().requireIntent(expected)
        return IndependentCaptureGrant(grant.proof) {
            environment().requireIntent(expected)
            grant.snapshot().also { environment().requireIntent(expected) }
        }
    }
    fun verifyLocally(password: CharArray): String {
        val proof = unlock(password).recheck()
        return "独立身份与可信期限已核验，人员 ${proof.identity.operatorId}。独立记录始终待对账。"
    }
    internal fun verifyCard(card: IndependentCredentialCard, expected: JSONObject): String {
        environment().requireIntent(expected)
        val password = card.credential(expected)
        try {
            verifyLocally(password)
            environment().requireIntent(expected)
            return "独立凭据卡经原校验器核验通过；仅为本次身份核对，创建任务仍须原核验，独立记录始终待对账。"
        } finally { password.fill('\u0000') }
    }
    fun revoke() = store.revoke()
    fun status() = store.status()
    companion object {
        private val instances = java.util.WeakHashMap<Context, IndependentCredentials>()
        @Synchronized fun shared(context: Context, session: PdaSession, recovery: RecoveryProtection): IndependentCredentials =
            instances.getOrPut(context.applicationContext) { IndependentCredentials(context.applicationContext, session, recovery) }
    }
}
