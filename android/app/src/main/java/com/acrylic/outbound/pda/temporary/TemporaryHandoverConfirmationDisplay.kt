package com.acrylic.outbound.pda.temporary

import org.json.JSONObject

/** Shows original approved rows in their recorded order, without deriving a new physical decision. */
object TemporaryHandoverConfirmationDisplay {
    fun lines(row: TemporaryHandoverStage): String {
        val lines = JSONObject(row.manifestJson).getJSONArray("checklist")
        val body = JSONObject(row.payloadBytes.toString(Charsets.UTF_8))
        val scans = body.optJSONArray("scans")
        val barcodes = mutableMapOf<String, String>()
        if (scans != null) for (index in 0 until scans.length()) {
            val scan = scans.getJSONObject(index)
            barcodes[scan.getString("baleId")] = scan.getString("barcode")
        }
        return (0 until lines.length()).joinToString("\n") { index ->
            val line = lines.getJSONObject(index)
            check(line.getString("state") == "RETAINED") { "清单包含尚未支持的处置，请保留资料" }
            val bale = line.getString("baleId")
            "${index + 1}. 保留 ${barcodes[bale] ?: bale} · 发货号 ${line.getString("deliveryCode")} · 周期 ${line.getString("dispatchCycle") }"
        }.ifBlank { "批准保留清单为空，请核对原作业资料。" }
    }
}
