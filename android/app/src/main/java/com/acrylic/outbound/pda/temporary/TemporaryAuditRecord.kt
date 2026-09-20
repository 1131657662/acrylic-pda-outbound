package com.acrylic.outbound.pda.temporary

import androidx.room.Embedded
import org.json.JSONObject

data class TemporaryAuditRecord(@Embedded val event: TemporaryEvent, val creatorDeviceId: String,
    val authorizationId: String, val sourceOperatorId: String) {
    fun json(): JSONObject {
        val e = event
        check(e.originDeviceId == creatorDeviceId && e.operatorId == sourceOperatorId) { "临时操作记录的原身份不一致，请保留资料核对" }
        return JSONObject().put("id", e.id).put("captureId", e.captureId ?: JSONObject.NULL)
            .put("source", JSONObject().put("kind", "TEMP").put("creatorDeviceId", creatorDeviceId).put("clientOrderId", e.clientOrderId))
            .put("authorizationId", authorizationId).put("originDeviceId", e.originDeviceId).put("operatorId", e.operatorId)
            .put("localSeq", e.localSeq.toString()).put("type", e.type).put("detail", e.detail)
            .put("wallMs", e.wallMs.toString()).put("elapsedMs", e.elapsedMs.toString()).put("boot", e.boot)
    }
}
