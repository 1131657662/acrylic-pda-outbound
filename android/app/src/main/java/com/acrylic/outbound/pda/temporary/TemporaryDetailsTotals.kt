package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.work.ScanProductTotal
import com.acrylic.outbound.pda.work.ScanTotal

/** Read verified retained facts without requiring or granting permission to start a new work round. */
internal class TemporaryDetailsTotals(private val store: TemporaryWorkStore, private val id: String) {
    fun products(original: TemporaryDetailsOriginal?): List<ScanProductTotal> {
        val result = store.db.temporaryScans().products(id).associateBy { it.deliveryCode }.toMutableMap()
        original?.retained.orEmpty().forEach {
            val code = it.getString("deliveryCode"); val prior = result[code] ?: ScanProductTotal(code, 0, 0)
            result[code] = ScanProductTotal(code, Math.addExact(prior.count, 1), Math.addExact(prior.grams, it.getString("weightGrams").toLong()))
        }
        return result.values.sortedBy { it.deliveryCode }
    }
    fun total(products: List<ScanProductTotal>): ScanTotal = products.fold(ScanTotal(0, 0)) { total, row ->
        ScanTotal(Math.addExact(total.count, row.count), Math.addExact(total.grams, row.grams))
    }
}
