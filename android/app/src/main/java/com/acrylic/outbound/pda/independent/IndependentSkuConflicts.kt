package com.acrylic.outbound.pda.independent

internal object IndependentSkuConflicts {
    fun apply(rows: List<IndependentSkuRow>): List<IndependentSkuRow> {
        val barcodes = mutableMapOf<String, IndependentSkuBale>()
        val mappings = mutableMapOf<String, Pair<String, String>>()
        val badCodes = mutableSetOf<String>()
        val badMappings = mutableSetOf<String>()
        rows.forEach { row -> row.parsedFields?.let { bale ->
            val previous = barcodes.putIfAbsent(bale.barcode, bale)
            if (previous != null && previous != bale) badCodes.add(bale.barcode)
            val mapping = bale.model to bale.name
            val oldMapping = mappings.putIfAbsent(bale.deliveryCode, mapping)
            if (oldMapping != null && oldMapping != mapping) badMappings.add(bale.deliveryCode)
        } }
        val seen = mutableSetOf<String>()
        return rows.map { classify(it, seen, badCodes to badMappings) }
    }
    private fun classify(row: IndependentSkuRow, seen: MutableSet<String>, conflicts: Pair<Set<String>, Set<String>>): IndependentSkuRow {
        val bale = row.parsedFields ?: return row
        if (bale.barcode in conflicts.first) return row.copy(errorCode = "DUPLICATE_CONFLICT", errorDetail = "同条码存在不同数据，相关行均阻断")
        if (bale.deliveryCode in conflicts.second) return row.copy(errorCode = "MAPPING_CONFLICT", errorDetail = "同发货代码对应多个型号或品名，相关行均阻断")
        if (!seen.add(bale.barcode)) return row.copy(errorCode = "DUPLICATE_IDENTICAL", errorDetail = "相同条码及字段重复，需要明确确认去重")
        return row
    }
}
