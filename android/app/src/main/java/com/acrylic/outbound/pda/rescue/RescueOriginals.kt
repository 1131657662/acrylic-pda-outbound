package com.acrylic.outbound.pda.rescue

import org.json.JSONObject

internal object RescueOriginals {
    fun frozen(id: String, payload: String, facts: Pair<String, String?>): Map<String, ByteArray> {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        check(RescueBundle.sha(bytes) == facts.first) { "原冻结正文摘要不一致，请保留资料" }
        RescuePublicEvidence.verify(JSONObject(payload))
        facts.second?.let { RescuePublicEvidence.verify(JSONObject(it)) }
        return buildMap {
            put("submission.json", bytes)
            put("submission-info.json", JSONObject().put("id", id).put("sha256", facts.first).toString().toByteArray(Charsets.UTF_8))
            facts.second?.let { put("receipt.json", it.toByteArray(Charsets.UTF_8)) }
        }
    }
    fun publicSource(type: String, text: String): JSONObject {
        RescuePublicEvidence.verify(JSONObject(text))
        return JSONObject().put("type", type).put("sha256", RescueBundle.sha(text.toByteArray(Charsets.UTF_8))).put("originalText", text)
    }

    fun authorization(text: String): JSONObject {
        val original = JSONObject(text)
        val fields = listOf("id", "orderId", "operatorId", "deviceId", "epoch", "revision", "offlineAuthorizationId",
            "inventorySnapshotId", "serverRecoveryEpoch", "startOrderScanSeq", "workRound", "issuedAt", "expiresAt", "status")
        return JSONObject().put("type", "AUTHORIZATION_IDENTIFIERS").put("facts", JSONObject().apply {
            fields.filter(original::has).forEach { put(it, original.get(it).toString()) }
        })
    }
}
