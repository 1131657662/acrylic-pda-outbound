package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.preparation.InventoryDecoder
import com.acrylic.outbound.pda.preparation.VerifiedPackage
import org.json.JSONArray

/** Existing preparation is evidence too; retry never repairs or overwrites a damaged copy. */
internal class HandoverPreparationIntegrity(private val db: PdaDatabase) {
    fun verify(input: VerifiedPackage) {
        val id = input.assignment.getString("id")
        val prepared = checkNotNull(db.preparations().order(id))
        check(prepared.assignmentJson == input.assignment.toString() &&
            prepared.orderId == input.assignment.getString("orderId") &&
            prepared.operatorId == input.assignment.getString("operatorId") &&
            prepared.deviceId == input.assignment.getString("deviceId") &&
            prepared.planJson == input.payloads.getValue("plan").toString(Charsets.UTF_8) &&
            prepared.manifestJson == input.receipt.getString("manifest") &&
            prepared.signedManifest == input.receipt.getString("signedManifest") &&
            prepared.signingKeyJson == input.key.toString()) { "本机目标资料与已验证下载不一致" }
        val bales = InventoryDecoder.decode(id, JSONArray(input.payloads.getValue("inventory").toString(Charsets.UTF_8)),
            JSONArray(input.payloads.getValue("availability").toString(Charsets.UTF_8)))
        check(bales.all { db.preparations().bale(id, it.barcode) == it }) { "本机固定库存资料损坏，请保留资料" }
    }
}
