package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.continuation.HandoverReceiptVerifier
import org.json.JSONObject
import java.time.Instant

/** A supervisor fault receipt has its own domain and identity; it never represents an originating PDA ACK. */
internal object TemporaryHandoverFault {
    fun verify(manifest: JSONObject, key: JSONObject): JSONObject {
        check(!manifest.has("stopReceipt") && manifest.getString("stopKind") == "FAULT_FENCE")
        val receipt = HandoverReceiptVerifier.verify(manifest.getJSONObject("faultReceipt"), key,
            "acrylic-outbound-handover-receipt", "acrylic-outbound-server")
        check(receipt.getString("commandType") == "FAULT_FENCE" && receipt.getString("status") == "RECORDED")
        val decision = receipt.getJSONObject("businessDecision")
        check(decision.getString("status") == "FAULT_FENCED" && decision.getString("stopKind") == "FAULT_FENCE" &&
            decision.get("oldDeviceAcknowledged") == false)
        val context = receipt.getJSONObject("caseContext")
        check(decision.getString("caseId") == manifest.getString("caseId") &&
            context.getString("stopState") == "REQUESTED" && context.getString("sourceKind") == "TEMP")
        val source = decision.getJSONObject("source"); val actor = receipt.getJSONObject("actor")
        check(actor.isNull("deviceId") && actor.isNull("sessionId") &&
            actor.getString("principalId") != source.getString("operatorId") &&
            actor.getString("principalId") != context.getString("requestedBy"))
        val permissions = actor.getJSONObject("permissionSnapshot").getJSONArray("effectivePermissions")
        check((0 until permissions.length()).any { permissions.getString(it) == "order:handover" })
        check(source.getString("deviceId") == decision.getString("sourceDeviceId") &&
            TemporaryVerifier.positive(decision.getString("disabledDeviceVersion")) > 0)
        custody(decision.getJSONObject("physicalControl"), Instant.parse(receipt.getString("recordedAt")))
        return receipt
    }

    private fun custody(control: JSONObject, recorded: Instant) {
        check(control.getString("deviceState") == "POWERED_OFF_IN_CUSTODY" && control.getString("unresolvedInputCount") == "0")
        check(!Instant.parse(control.getString("controlledAt")).isAfter(recorded))
        for (field in listOf("deviceLocation", "goodsLocation"))
            check(control.getString(field).isNotBlank() && control.getString(field).length <= 200)
    }
}
