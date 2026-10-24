package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.independent.IndependentCredentialSnapshot
import com.acrylic.outbound.pda.temporary.TemporaryRequest
import org.json.JSONObject
import java.time.Instant

/** Read-only name bindings from original containers; absent old evidence is never fabricated. */
internal object RescueTextNames {
    fun temporary(request: TemporaryRequest): Map<String, JSONObject> {
        val receipt = JSONObject(checkNotNull(request.receiptJson))
        if (!receipt.has("operatorNameProof") || receipt.isNull("operatorNameProof")) return emptyMap()
        val manifest = JSONObject(receipt.getString("manifest"))
        val binding = RescueOperatorNameProof.Binding("TEMP", receipt.getString("authorizationId"), request.userId,
            request.deviceId, request.warehouseId, receipt.getString("offlineToken"), Instant.parse(manifest.getString("issuedAt")))
        return entry(receipt.getString("operatorNameProof"), JSONObject(request.signingKeyJson), binding)
    }

    fun independent(db: PdaDatabase, id: String): Map<String, JSONObject> {
        val task = checkNotNull(db.independentTasks().task(id))
        val request = db.independentTasks().request(id) ?: return emptyMap()
        val original = IndependentCredentialSnapshot.read(request.authorizationJson)
        val token = original.operatorNameProof ?: return emptyMap()
        check(original.proof.originalToken == task.signedCredential && original.trustedKeyJson == task.trustedKeyJson &&
            original.localSessionId == task.localSessionId) { "原任务姓名证明容器不一致" }
        val binding = RescueOperatorNameProof.Binding("INDEPENDENT", task.credentialId, task.operatorId, task.deviceId,
            task.warehouseId, task.signedCredential, original.proof.issued)
        return entry(token, JSONObject(task.trustedKeyJson), binding)
    }

    fun attach(order: JSONObject, names: Map<String, JSONObject>) {
        val all = order.optJSONObject("textOperatorNames") ?: JSONObject()
        names.forEach { (id, value) ->
            check(!all.has(id) || all.getJSONObject(id).toString() == value.toString()) { "原姓名证明重复冲突" }
            all.put(id, value)
        }
        if (all.length() > 0) order.put("textOperatorNames", all)
    }

    private fun entry(token: String, key: JSONObject, binding: RescueOperatorNameProof.Binding): Map<String, JSONObject> {
        val name = RescueOperatorNameProof.verify(token, key, binding)
        return mapOf(binding.authorizationId to JSONObject().put("displayName", name).put("operatorNameProof", token))
    }
}
