package com.acrylic.outbound.pda.rescue

import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID

internal data class RescueRequestInput(val identity: RescueReadIdentity, val target: RescueTarget,
    val rescuerId: String, val reason: String)
internal data class RescueRequestIntent(val originalText: String, val signature: String,
    val keySha256: String, val epoch: String, val receipt: String? = null, val material: String? = null) {
    val document: JSONObject get() = RescueSignedJson.read(originalText.toByteArray(Charsets.UTF_8))
    val key: String get() = document.getString("requestKey")
    val body: JSONObject get() = JSONObject().put("originalText", originalText).put("signature", signature)
    val target: RescueTarget get() = document.getJSONArray("orderScope").getJSONObject(0).let {
        RescueTarget(it.getString("source"), it.getString("localOrderId"))
    }
    fun belongs(identity: RescueReadIdentity): Boolean = document.let {
        it.getString("operatorId") == identity.operatorId && it.getString("deviceId") == identity.deviceId &&
            it.getString("warehouseId") == identity.warehouseId
    }
}

internal object RescueRequestDocument {
    fun create(input: RescueRequestInput, epoch: String, signer: RescueSigner): RescueRequestIntent {
        check(epoch.matches(Regex("[1-9][0-9]*"))) { "恢复代次无效，请保留原资料" }
        listOf(input.identity.deviceId, input.identity.operatorId, input.identity.warehouseId,
            input.rescuerId, input.target.localOrderId).forEach(::uuid)
        check(input.rescuerId != input.identity.operatorId) { "救援人须与原操作员不同" }
        check(input.reason.isNotBlank() && input.reason.length <= 1000) { "请填写不超过1000字的申请原因" }
        check(input.target.source in setOf("FORMAL", "TEMP")) { "请选择原正式或临时订单" }
        val text = JSONObject().put("purpose", "PDA_RESCUE_REQUEST_V1").put("requestKey", UUID.randomUUID().toString())
            .put("deviceId", input.identity.deviceId).put("operatorId", input.identity.operatorId)
            .put("warehouseId", input.identity.warehouseId).put("rescuerId", input.rescuerId)
            .put("orderScope", JSONArray().put(JSONObject().put("source", input.target.source)
                .put("localOrderId", input.target.localOrderId))).put("reason", input.reason).toString()
        return RescueRequestIntent(text, signer.sign("PDA_RESCUE_REQUEST_V1\n$text"), RescueBundle.sha(signer.publicKey), epoch)
    }
    fun verify(intent: RescueRequestIntent, identity: RescueReadIdentity, publicKey: ByteArray) {
        check(intent.belongs(identity) && intent.keySha256 == RescueBundle.sha(publicKey)) { "原申请身份或设备钥已变化，请保留原件" }
        val doc = intent.document
        check(doc.keys().asSequence().toSet() == setOf("purpose", "requestKey", "deviceId", "operatorId",
            "warehouseId", "rescuerId", "orderScope", "reason"))
        check(doc.getString("purpose") == "PDA_RESCUE_REQUEST_V1" && intent.epoch.matches(Regex("[1-9][0-9]*")))
        uuid(intent.key); uuid(doc.getString("rescuerId")); uuid(intent.target.localOrderId)
        check(doc.getString("rescuerId") != identity.operatorId && doc.getString("reason").isNotBlank() && doc.getString("reason").length <= 1000)
        val scope = doc.getJSONArray("orderScope")
        check(scope.length() == 1 && scope.getJSONObject(0).keys().asSequence().toSet() == setOf("source", "localOrderId"))
        check(intent.target.source in setOf("FORMAL", "TEMP"))
        check(Signature.getInstance("SHA256withECDSA").run {
            initVerify(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKey)))
            update("PDA_RESCUE_REQUEST_V1\n${intent.originalText}".toByteArray(Charsets.UTF_8))
            verify(Base64.getDecoder().decode(intent.signature))
        }) { "原申请设备签名不匹配，请保留原件" }
    }
    fun uuid(text: String) { check(UUID.fromString(text).toString() == text) { "人员或订单编号须为规范UUID" } }
}
