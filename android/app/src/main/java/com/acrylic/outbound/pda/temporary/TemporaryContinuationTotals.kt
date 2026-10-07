package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.work.ScanProductTotal
import com.acrylic.outbound.pda.work.ScanTotal
import org.json.JSONObject

/** Fixed retained facts contribute to totals without being owned or edited by the new draft. */
internal class TemporaryContinuationTotals(private val store: TemporaryWorkStore) {
    fun page(id: String, after: Long): List<TemporaryScan> {
        val draft = checkNotNull(store.db.temporaryDrafts().get(id))
        val baseline = TemporaryContinuationBaseline(store).read(draft)
        val rows = baseline?.original?.getJSONArray("scans")
        val prefix = if (rows == null) emptyList() else (0 until rows.length()).map { rows.getJSONObject(it) }
            .filter { it.getString("orderScanSeq").toLong() > after }.take(50).map { row ->
                TemporaryScan(row.getString("captureId"), checkNotNull(baseline).link.sourceClientOrderId,
                    row.getString("orderScanSeq").toLong(), row.getString("barcode"), row.getString("baleId"),
                    row.getString("deliveryCode"), row.getString("model"), row.getString("name"), row.getString("rawWeight"),
                    row.getString("weightGrams").toLong(), row.getString("expectedDispatchCycle").toLong(),
                    row.getString("savedAt"), row.getString("localSeq").toLong(), row.getString("status"))
            }
        return (prefix + store.db.temporaryScans().page(id, after)).sortedBy { it.orderScanSeq }.take(50)
    }

    fun total(id: String): ScanTotal {
        val current = store.db.temporaryScans().total(id)
        val rows = retained(id)
        return rows.fold(current) { sum, row -> ScanTotal(Math.addExact(sum.count, 1),
            Math.addExact(sum.grams, row.getString("weightGrams").toLong())) }
    }

    fun products(id: String): List<ScanProductTotal> {
        val totals = store.db.temporaryScans().products(id).associateBy { it.deliveryCode }.toMutableMap()
        retained(id).forEach { row ->
            val code = row.getString("deliveryCode"); val old = totals[code] ?: ScanProductTotal(code, 0, 0)
            totals[code] = ScanProductTotal(code, Math.addExact(old.count, 1),
                Math.addExact(old.grams, row.getString("weightGrams").toLong()))
        }
        return totals.values.sortedBy { it.deliveryCode }
    }

    private fun retained(id: String): List<JSONObject> {
        val draft = checkNotNull(store.db.temporaryDrafts().get(id))
        val baseline = TemporaryContinuationBaseline(store).read(draft) ?: return emptyList()
        val scans = baseline.original.getJSONArray("scans")
        return (0 until scans.length()).map { scans.getJSONObject(it) }.filter { it.getString("status") == "ACTIVE" }
    }
}
