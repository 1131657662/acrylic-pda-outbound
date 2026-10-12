package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.network.SessionIdentity
import com.acrylic.outbound.pda.security.DeviceKeys
import org.json.JSONObject
import java.util.Base64

/** Original-person product consumer; stored intents never contain session credentials. */
internal class RescueRequestRepository(private val app: PdaApplication) {
    private val store = RescueRequestStore(RescueRequestVault(app))
    private val keys = DeviceKeys()
    private fun identity(session: JSONObject) = RescueReadIdentity(session.getString("deviceId"), session.getString("userId"), session.getString("warehouseId"))
    private fun publicKey(): ByteArray = Base64.getDecoder().decode(keys.publicKey(false).also(app.session.settings::verifyKey))
    fun ownOrders(after: String = ""): RescueOrderPage {
        val session = app.session.requireSession()
        return RescueOrders(app.database).page(RescueReadGrant(identity(session)) { SessionIdentity.requireSame(session, app.session.saved()) }, after)
    }
    fun list(): List<RescueRequestIntent> {
        val session = app.session.requireSession(); val result = store.list(identity(session))
        SessionIdentity.requireSame(session, app.session.saved()); return result
    }
    fun prepare(target: RescueTarget, rescuerId: String, reason: String): RescueRequestIntent {
        val session = app.session.requireSession(); val actor = identity(session)
        check(app.session.settings.device == actor.deviceId) { "原设备登记已变化，请保留原申请" }
        RescueRoomSnapshot(app.database).read(target, RescueReadGrant(actor) { SessionIdentity.requireSame(session, app.session.saved()) })
        val input = RescueRequestInput(actor, target, rescuerId.trim(), reason.trim())
        val intent = RescueRequestDocument.create(input, session.getString("serverRecoveryEpoch"), RescueSigner(publicKey(), keys::sign))
        SessionIdentity.requireSame(session, app.session.saved())
        return store.prepare(intent).also { RescueRequestDocument.verify(it, actor, publicKey()) }
    }
    fun send(key: String): RescueRequestIntent {
        val session = app.session.requireSession(); val intent = store.get(key)
        check(app.session.settings.device == session.getString("deviceId")) { "原设备登记已变化，请保留原申请" }
        RescueRequestDocument.verify(intent, identity(session), publicKey())
        SessionIdentity.requireSame(session, app.session.saved())
        val response = app.session.call("pda/rescue-requests", intent.body, intent.epoch,
            extraHeaders = mapOf("Idempotency-Key" to intent.key), expected = session)
        SessionIdentity.requireSame(session, app.session.saved())
        return store.receipt(key, response)
    }
    fun refresh(key: String): RescueRequestIntent {
        val session = app.session.requireSession(); val intent = store.get(key)
        check(app.session.settings.device == session.getString("deviceId")) { "原设备登记已变化，请保留原申请" }
        RescueRequestDocument.verify(intent, identity(session), publicKey())
        val receipt = JSONObject(checkNotNull(intent.receipt) { "服务端回执未确认，请先按原键提交或重试" })
        val response = app.session.call("pda/rescue-requests/${receipt.getString("requestId")}", expected = session)
        SessionIdentity.requireSame(session, app.session.saved())
        verifyMaterial(intent, response, app.session.requireSession().getJSONObject("signingKey"))
        return store.material(key, response)
    }
    private fun verifyMaterial(intent: RescueRequestIntent, material: JSONObject, trustedKey: JSONObject) {
        if (material.isNull("authorization")) {
            check(material.getString("status") == "REQUESTED" && material.isNull("revocation")); return
        }
        val original = intent.document
        val token = material.getJSONObject("authorization").getString("signedAuthorization")
        val grant = RescueDelegation.verify(token, trustedKey, original.getString("deviceId") to intent.keySha256)
        check(grant.rescuerId == original.getString("rescuerId") && grant.identity.operatorId == original.getString("operatorId") &&
            grant.identity.warehouseId == original.getString("warehouseId") && grant.targets == setOf(intent.target) &&
            material.getJSONObject("authorization").getString("grantId") == grant.grantId)
        if (!material.isNull("revocation")) {
            RescueDelegation.verifyRevocation(material.getJSONObject("revocation").getString("signedRevocation"), trustedKey, grant)
            check(material.getString("status") == "REVOKED")
        } else check(material.getString("status") in setOf("AUTHORIZED", "EXPIRED"))
    }
    companion object {
        private val instances = java.util.WeakHashMap<PdaApplication, RescueRequestRepository>()
        @Synchronized fun shared(app: PdaApplication) = instances.getOrPut(app) { RescueRequestRepository(app) }
    }
}
