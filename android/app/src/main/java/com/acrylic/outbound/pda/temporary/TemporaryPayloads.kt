package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.*
import java.nio.ByteBuffer
import java.util.Base64

object TemporaryPayloads {
    fun verify(manifest: JSONObject, payloads: Map<String, ByteArray>) {
        check(payloads.keys == TemporaryVerifier.parts) { "临时资料文件不完整" }
        val decoded = TemporaryVerifier.descriptors(manifest).associate { part ->
            val name = part.getString("name"); val bytes = payloads.getValue(name)
            check(bytes.size == part.getInt("bytes") && JwtVerifier.sha(bytes) == part.getString("sha256")) { "激活前临时资料摘要不一致" }
            val text = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
            val value = if (name in setOf("inventory", "mapping")) JSONArray(text) else JSONObject(text)
            val rows = if (value is JSONArray) value.length() else if (name == "availability") (value as JSONObject).getJSONArray("items").length() else 1
            check(rows == part.getInt("rows")) { "临时资料行数不一致" }; name to value
        }
        val states = availability(decoded.getValue("availability") as JSONObject, manifest)
        inventory(decoded.getValue("inventory") as JSONArray, states)
        mapping(decoded.getValue("mapping") as JSONArray)
        policy(decoded.getValue("policy") as JSONObject, manifest)
        rules(decoded.getValue("rules") as JSONObject, manifest)
    }
    private fun availability(data: JSONObject, manifest: JSONObject): Map<String, JSONObject> {
        val identity = manifest.getJSONObject("identity")
        check(data.getString("streamType") == "BARCODE" && data.getString("recoveryEpoch") == identity.getString("serverRecoveryEpoch") &&
            data.getString("warehouseId") == identity.getString("warehouseId")) { "临时可用状态来源错误" }
        val high = data.getString("highWatermark").toLong(); check(high >= 0 && high.toString() == data.getString("highWatermark"))
        val cursor = String(Base64.getUrlDecoder().decode(data.getString("nextCursor")), Charsets.US_ASCII)
        check(cursor == "1|${data.getString("recoveryEpoch")}|${data.getString("warehouseId")}|BARCODE|$high") { "临时状态游标错误" }
        val rows = data.getJSONArray("items"); val result = HashMap<String, JSONObject>(); val ids = HashSet<String>()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i); val barcode = row.getString("barcode")
            check(barcode.matches(Regex("[A-Z0-9]{11}")) && result.put(barcode, row) == null)
            check(ids.add(TemporaryVerifier.uuid(row.getString("baleId")))) { "临时状态包标识重复" }
            TemporaryVerifier.positive(row.getString("dispatchCycle")); TemporaryVerifier.positive(row.getString("rowVersion"))
            check(row.getString("status") in setOf("AVAILABLE", "UNAVAILABLE", "RESERVED", "QUARANTINED", "DISPATCHED"))
        }
        return result
    }
    private fun inventory(rows: JSONArray, states: Map<String, JSONObject>) {
        val seen = HashSet<String>()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i); val barcode = row.getString("barcode")
            check(states.containsKey(barcode) && seen.add(barcode)) { "临时库存状态缺失或条码重复" }
            check(row.getString("deliveryCode") == barcode.take(3))
            check(row.getString("model").isNotBlank() && row.getString("name").isNotBlank())
            val raw = row.getString("rawWeight"); check(raw.matches(Regex("[0-9]+(\\.[0-9]{1,3})?")))
            check(raw.toBigDecimal().movePointRight(3).longValueExact() == TemporaryVerifier.positive(row.getString("weightGrams"))) { "临时库存重量不一致" }
        }
    }
    private fun mapping(rows: JSONArray) {
        val codes = HashSet<String>()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i); val code = row.getString("deliveryCode")
            check(code.matches(Regex("[A-Z0-9]{3}")) && codes.add(code))
            TemporaryVerifier.uuid(row.getString("id")); TemporaryVerifier.uuid(row.getString("productId"))
            TemporaryVerifier.positive(row.getString("version")); check(row.get("enabled") is Boolean && row.getBoolean("current"))
            check(row.getString("model").isNotBlank() && row.getString("name").isNotBlank())
        }
    }
    private fun policy(data: JSONObject, manifest: JSONObject) {
        TemporaryVerifier.uuid(data.getString("id"))
        check(data.getString("version") == manifest.getString("policyVersion") && data.getInt("schemaVersion") == 1 && data.getInt("offlineHours") == 48)
        check(data.getString("shortShipmentPolicy") == "SUPERVISOR_APPROVAL" && data.getString("concurrencyMode") == "PHYSICAL_SEPARATION")
        check(data.getString("scanMode") == "AUTO_SCAN_MANUAL_CONFIRM" && data.get("autoDeductInventory") is Boolean)
    }
    private fun rules(data: JSONObject, manifest: JSONObject) {
        check(manifest.getString("barcodeRuleVersion") == "1" && data.getInt("version") == 1 && data.getString("barcodePattern") == "[A-Z0-9]{11}")
        check(data.getInt("deliveryCodePrefixLength") == 3 && data.getString("weightUnit") == "INTEGER_GRAMS" && data.getInt("maxWeightDecimals") == 3)
        check(data.getString("scanMode") == "AUTO_SCAN_MANUAL_CONFIRM")
    }
}
