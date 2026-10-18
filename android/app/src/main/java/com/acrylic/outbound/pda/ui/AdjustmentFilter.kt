package com.acrylic.outbound.pda.ui

/** Original saved rows only; bounded pages never renumber or create captures. */
data class AdjustmentFilter(val sequence: Long? = null, val product: String = "") {
    fun page(after: Long, read: (Long) -> List<AdjustmentRow>): List<AdjustmentRow> {
        val found = ArrayList<AdjustmentRow>(20)
        var cursor = after
        while (found.size < 20) {
            val rows = read(cursor)
            if (rows.isEmpty()) break
            check(rows.first().sequence > cursor && rows.zipWithNext().all { it.first.sequence < it.second.sequence }) {
                "原记录顺序异常，请保留资料"
            }
            for (row in rows) {
                if (sequence != null && row.sequence > sequence) return found
                if ((sequence == null || row.sequence == sequence) && row.product.contains(product, ignoreCase = true)) found.add(row)
                if (found.size == 20) return found
            }
            cursor = rows.last().sequence
        }
        return found
    }

    companion object {
        fun parse(sequence: String, product: String): AdjustmentFilter {
            val number = sequence.trim()
            val value = if (number.isEmpty()) null else number.toLongOrNull()?.takeIf { it > 0 }
                ?: error("请输入有效原序号")
            val text = product.trim()
            require(text.length <= 128 && text.none { it.isISOControl() }) { "产品筛选最多128字，不能含控制字符" }
            return AdjustmentFilter(value, text)
        }
    }
}
