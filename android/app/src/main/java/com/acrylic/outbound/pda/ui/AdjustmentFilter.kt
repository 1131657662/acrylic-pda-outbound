package com.acrylic.outbound.pda.ui

enum class AdjustmentStatusFilter(val code: String, val label: String) {
    ALL("ALL", "全部"),
    ACTIVE("ACTIVE", "有效"),
    VOID("VOID", "已撤销"),
    RETAINED("RETAINED", "主管保留");

    fun matches(rowStatus: String): Boolean = this == ALL || code == rowStatus

    companion object {
        fun fromCode(code: String?): AdjustmentStatusFilter =
            values().firstOrNull { it.code == code } ?: ALL

        fun fromPosition(position: Int): AdjustmentStatusFilter = values().getOrNull(position) ?: ALL
    }
}

/** Original saved rows only; bounded pages never renumber or create captures. */
data class AdjustmentFilter(
    val sequence: Long? = null,
    val product: String = "",
    val status: AdjustmentStatusFilter = AdjustmentStatusFilter.ALL
) {
    fun page(after: Long, read: (Long) -> List<AdjustmentRow>): AdjustmentPage {
        val found = ArrayList<AdjustmentRow>(PAGE_SIZE)
        var cursor = after
        while (true) {
            val rows = read(cursor)
            if (rows.isEmpty()) return AdjustmentPage(found, false)
            verifyOrder(rows, cursor)
            for (row in rows) {
                if (pastSequence(row)) return AdjustmentPage(found, false)
                if (!matches(row)) continue
                if (found.size == PAGE_SIZE) return AdjustmentPage(found, true)
                found.add(row)
            }
            cursor = rows.last().sequence
        }
    }

    private fun matches(row: AdjustmentRow): Boolean =
        (sequence == null || sequence == row.sequence) &&
            row.product.contains(product, ignoreCase = true) && status.matches(row.status)

    private fun pastSequence(row: AdjustmentRow): Boolean = sequence != null && row.sequence > sequence

    private fun verifyOrder(rows: List<AdjustmentRow>, cursor: Long) {
        check(rows.first().sequence > cursor && rows.zipWithNext().all { it.first.sequence < it.second.sequence }) {
            "原记录顺序异常，请保留资料"
        }
    }

    companion object {
        const val PAGE_SIZE = 20

        fun parse(
            sequence: String,
            product: String,
            status: AdjustmentStatusFilter = AdjustmentStatusFilter.ALL
        ): AdjustmentFilter {
            val number = sequence.trim()
            val value = if (number.isEmpty()) null else number.toLongOrNull()?.takeIf { it > 0 }
                ?: error("请输入有效原序号")
            val text = product.trim()
            require(text.length <= 128 && text.none { it.isISOControl() }) { "产品筛选最多128字，不能含控制字符" }
            return AdjustmentFilter(value, text, status)
        }
    }
}

data class AdjustmentPage(val rows: List<AdjustmentRow>, val hasMore: Boolean)
