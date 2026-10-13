package com.acrylic.outbound.pda.independent

import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class IndependentTaskCommand(val id: String, val importId: String, val vehicle: String,
    val reason: String, val quantities: Map<String, Long>) {
    fun validate() {
        require(UUID.fromString(id).toString() == id && UUID.fromString(importId).toString() == importId)
        require(vehicle.length in 1..40 && vehicle == vehicle.trim() && vehicle.none { Character.isISOControl(it) }) { "车号须为1至40位可见字符" }
        require(reason.length in 1..500 && reason == reason.trim() && reason.none { Character.isISOControl(it) && it != '\n' }) { "原因须为1至500位可见文字" }
        require(Charsets.UTF_8.newEncoder().canEncode(vehicle) && Charsets.UTF_8.newEncoder().canEncode(reason)) { "车号和原因含无效文字编码" }
        require(quantities.size in 1..200 && quantities.all { (code, count) -> code.matches(Regex("[0-9A-Z]{3}")) && count in 1..100000 }) { "每个计划产品须填写1至100000包" }
        require(total() <= 100000) { "整单计划不得超过100000包" }
    }
    fun total() = quantities.values.fold(0L, Math::addExact)
    fun json(): JSONObject = JSONObject().put("id", id).put("importId", importId).put("vehicle", vehicle).put("reason", reason)
        .put("lines", JSONArray().also { lines -> quantities.toSortedMap().forEach { (code, count) ->
            lines.put(JSONObject().put("code", code).put("count", count.toString()))
        } })
    // Length-prefixed UTF-8 fields and sorted products are stable across JSON implementations/processes.
    fun sha(): String {
        val fields = listOf("independent-task-command-v1", id, importId, vehicle, reason) +
            quantities.toSortedMap().flatMap { (code, count) -> listOf(code, count.toString()) }
        val bytes = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(bytes).use { out -> fields.forEach { field ->
            val raw = field.toByteArray(Charsets.UTF_8); out.writeInt(raw.size); out.write(raw)
        } }
        return JwtVerifier.sha(bytes.toByteArray())
    }
    companion object {
        fun read(value: JSONObject, validate: Boolean = true): IndependentTaskCommand {
            val lines = value.getJSONArray("lines"); val quantities = linkedMapOf<String, Long>()
            for (i in 0 until lines.length()) {
                val row = lines.getJSONObject(i); check(quantities.put(row.getString("code"), row.getString("count").toLong()) == null)
            }
            return IndependentTaskCommand(value.getString("id"), value.getString("importId"), value.getString("vehicle"), value.getString("reason"), quantities).also { if (validate) it.validate() }
        }
    }
}
