package com.acrylic.outbound.pda.ui

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.temporary.TemporaryRecoveredHandoverStage
import org.json.JSONObject

/** Displays the verified original checklist in its recorded order. */
internal class RecoveredHandoverConfirmationDisplay(private val db: PdaDatabase) {
    data class Display(val details: String, val lines: String)
    fun read(stage: TemporaryRecoveredHandoverStage): Display {
        val chunks = db.temporaryRecoveredHandoverChunks()
        val manifest = JSONObject(chunks.read(stage.caseId, "MANIFEST", stage.manifestLength).toString(Charsets.UTF_8))
        val payload = JSONObject(chunks.read(stage.caseId, "PAYLOAD", stage.payloadLength).toString(Charsets.UTF_8))
        val plan = payload.getJSONObject("temporaryOrder")
        val barcodes = mutableMapOf<String, String>(); val scans = payload.getJSONArray("scans")
        for (index in 0 until scans.length()) {
            val scan = scans.getJSONObject(index)
            if (scan.has("barcode")) barcodes[scan.getString("baleId")] = scan.getString("barcode")
        }
        val checklist = manifest.getJSONArray("checklist")
        val lines = (0 until checklist.length()).joinToString("\n") { index ->
            val line = checklist.getJSONObject(index); check(line.getString("state") == "RETAINED")
            val bale = line.getString("baleId")
            "${index + 1}. 保留 ${barcodes[bale] ?: bale} · 发货号 ${line.getString("deliveryCode")} · 周期 ${line.getString("dispatchCycle") }"
        }.ifBlank { "批准保留清单为空，请核对原作业资料。" }
        return Display("车号 ${plan.getString("vehicleNo")} · 柜号 ${plan.optString("containerNo")}\n保留清单 ${checklist.length()} 包", lines)
    }
}
