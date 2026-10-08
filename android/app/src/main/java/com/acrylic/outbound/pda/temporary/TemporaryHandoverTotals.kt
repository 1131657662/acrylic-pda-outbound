package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.work.ScanProductTotal
import com.acrylic.outbound.pda.work.ScanTotal
import org.json.JSONObject

/** Only the signed retained subset contributes; the immutable source remains separately owned. */
internal class TemporaryHandoverTotals(private val store: TemporaryWorkStore) {
    fun total(id: String): ScanTotal = baseline(id).retained.fold(store.db.temporaryScans().total(id)) { sum, row ->
        ScanTotal(Math.addExact(sum.count, 1), Math.addExact(sum.grams, row.getString("weightGrams").toLong()))
    }

    fun products(id: String): List<ScanProductTotal> {
        val totals = store.db.temporaryScans().products(id).associateBy { it.deliveryCode }.toMutableMap()
        for (row in baseline(id).retained) {
            val code = row.getString("deliveryCode"); val old = totals[code] ?: ScanProductTotal(code, 0, 0)
            totals[code] = ScanProductTotal(code, Math.addExact(old.count, 1),
                Math.addExact(old.grams, row.getString("weightGrams").toLong()))
        }
        return totals.values.sortedBy { it.deliveryCode }
    }

    fun page(id: String, after: Long): List<TemporaryScan> {
        val original = baseline(id).original
        val rows = original.getJSONArray("scans")
        val prefix = (0 until rows.length()).map { rows.getJSONObject(it) }
            .filter { it.getString("orderScanSeq").toLong() > after }.take(50)
            .map { scan(it, original.getJSONObject("source").getString("clientOrderId")) }
        return (prefix + store.db.temporaryScans().page(id, after)).sortedBy { it.orderScanSeq }.take(50)
    }

    private fun baseline(id: String) = TemporaryHandoverBaseline(store).read(checkNotNull(store.db.temporaryDrafts().get(id)))
    private fun scan(row: JSONObject, source: String) = TemporaryScan(row.getString("captureId"), source,
        row.getString("orderScanSeq").toLong(), row.getString("barcode"), row.getString("baleId"),
        row.getString("deliveryCode"), row.getString("model"), row.getString("name"), row.getString("rawWeight"),
        row.getString("weightGrams").toLong(), row.getString("expectedDispatchCycle").toLong(), row.getString("savedAt"),
        row.getString("localSeq").toLong(), row.getString("status"))
}
