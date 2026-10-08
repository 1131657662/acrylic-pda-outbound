package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Generated supervisor signature is a test authority; it does not impersonate the origin device. */
internal object TemporaryHandoverFaultFixture {
    fun install(f: TemporaryHandoverFixture) {
        val stop = f.manifest.getJSONObject("stopReceipt")
        val context = JSONObject(stop.getJSONObject("caseContext").toString())
            .put("stopState", "REQUESTED").put("requestedBy", UUID.randomUUID().toString())
        val control = JSONObject().put("deviceState", "POWERED_OFF_IN_CUSTODY").put("unresolvedInputCount", "0")
            .put("controlledAt", f.local.time.minusSeconds(1).toString()).put("deviceLocation", "合成保管柜").put("goodsLocation", "合成交接区")
        val decision = JSONObject().put("caseId", f.caseId).put("status", "FAULT_FENCED").put("stopKind", "FAULT_FENCE")
            .put("sourceDeviceId", f.sourceDevice).put("disabledDeviceVersion", "2").put("oldDeviceAcknowledged", false)
            .put("source", stop.getJSONObject("source")).put("physicalControl", control)
            .put("archivedEvidence", stop.getJSONObject("temporaryStop")).put("payloadSha256", JwtVerifier.sha(f.bytes(f.body)))
        val actor = JSONObject().put("principalId", UUID.randomUUID().toString()).put("deviceId", JSONObject.NULL)
            .put("sessionId", JSONObject.NULL).put("permissionSnapshot", JSONObject()
                .put("effectivePermissions", JSONArray().put("order:handover")))
        val receipt = JSONObject().put("commandType", "FAULT_FENCE").put("status", "RECORDED")
            .put("recordedAt", f.local.time.toString()).put("caseContext", context).put("actor", actor).put("businessDecision", decision)
        f.manifest.remove("stopReceipt")
        f.manifest.put("stopKind", "FAULT_FENCE").put("faultReceipt", f.signer.receipt(receipt))
        for (field in listOf("approvalReceipt", "preparationReceipt")) {
            f.manifest.getJSONObject(field).getJSONObject("caseContext").put("stopState", "FAULT_FENCED")
            f.resign(field)
        }
    }
}
