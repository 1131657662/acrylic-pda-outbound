package com.acrylic.outbound.pda

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.UUID

/** Publishes new synthetic packages without replacing earlier dispatch facts. */
internal object TemporaryAdjustmentStock {
    fun publish(web: FaultRestartWebClient, input: JSONObject): List<String> {
        val original = input.getJSONObject("oldBody").getJSONArray("scans")
        val serial = UUID.randomUUID().toString().replace("-", "").take(7).uppercase()
        val newCodes = listOf("003T$serial", "571T$serial")
        val bytes = ByteArrayOutputStream()
        repeat(original.length()) { index -> bytes.write(row(original.getJSONObject(index))) }
        newCodes.forEachIndexed { index, code ->
            bytes.write(row(JSONObject(original.getJSONObject(index).toString()).put("barcode", code)))
        }
        val id = web.uploadStock(bytes.toByteArray()); val diff = web.get("inventory/imports/$id/diff")
        val baseline = diff.getJSONObject("baseline")
        web.post("inventory/imports/$id/publish", JSONObject().put("expectedActiveSnapshot", baseline.get("activeSnapshotId"))
            .put("expectedMappingRevision", baseline.getString("mappingRevision")).put("acceptIdenticalDuplicates", true)
            .put("acceptMappingChanges", true).put("confirmedRemovedCount", diff.getInt("removed"))
            .put("reason", "隔离TEMP调整页面：新增合成包，保留正式单原出库事实"))
        return newCodes
    }
    private fun row(scan: JSONObject): ByteArray {
        val bytes = ByteArray(59) { ' '.code.toByte() }; val encoding = Charset.forName("GB18030")
        listOf(0 to scan.getString("model"), 20 to scan.getString("name"), 34 to scan.getString("barcode"),
            47 to scan.getString("rawWeight")).forEach { (offset, text) -> text.toByteArray(encoding).copyInto(bytes, offset) }
        bytes[57] = 13; bytes[58] = 10; return bytes
    }
}
