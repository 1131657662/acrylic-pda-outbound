package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import org.json.*

data class TemporaryProduct(val code: String, val productId: String, val model: String, val name: String)
data class TemporaryBale(val barcode: String, val baleId: String, val code: String, val model: String, val name: String,
    val rawWeight: String, val grams: Long, val cycle: Long, val rowVersion: Long, val status: String)

/** Built only from the original signed parts; new sync projections cannot replace this source. */
class TemporaryCatalog private constructor(val request: TemporaryRequest, val preparation: TemporaryPreparation,
    val manifest: JSONObject, val products: Map<String, TemporaryProduct>, val bales: Map<String, TemporaryBale>) {
    companion object {
        fun load(db: PdaDatabase, request: TemporaryRequest): TemporaryCatalog {
            val prepared = checkNotNull(db.temporary().prepared(request.key)) { "请先完整准备临时作业资料" }
            val manifest = TemporaryAckVerifier.declaration(prepared, request)
            prepared.ackJson?.let { TemporaryAckVerifier.verify(prepared, request, JSONObject(it)) }
            val payloads = TemporaryParts(db).read(prepared.authorizationId, manifest)
            TemporaryPayloads.verify(manifest, payloads)
            return TemporaryCatalog(request, prepared, manifest, products(payloads), bales(payloads))
        }
        private fun products(parts: Map<String, ByteArray>): Map<String, TemporaryProduct> {
            val rows = JSONArray(String(parts.getValue("mapping"), Charsets.UTF_8))
            return (0 until rows.length()).map { rows.getJSONObject(it) }.filter { it.getBoolean("enabled") }
                .associate { row -> row.getString("deliveryCode").let { code ->
                    code to TemporaryProduct(code, row.getString("productId"), row.getString("model"), row.getString("name")) } }
        }
        private fun bales(parts: Map<String, ByteArray>): Map<String, TemporaryBale> {
            val states = JSONObject(String(parts.getValue("availability"), Charsets.UTF_8)).getJSONArray("items")
            val byCode = (0 until states.length()).map { states.getJSONObject(it) }.associateBy { it.getString("barcode") }
            val rows = JSONArray(String(parts.getValue("inventory"), Charsets.UTF_8))
            return (0 until rows.length()).associate { index ->
                val row = rows.getJSONObject(index); val code = row.getString("barcode"); val state = byCode.getValue(code)
                code to TemporaryBale(code, state.getString("baleId"), row.getString("deliveryCode"), row.getString("model"),
                    row.getString("name"), row.getString("rawWeight"), row.getString("weightGrams").toLong(),
                    state.getString("dispatchCycle").toLong(), state.getString("rowVersion").toLong(), state.getString("status"))
            }
        }
    }
}
