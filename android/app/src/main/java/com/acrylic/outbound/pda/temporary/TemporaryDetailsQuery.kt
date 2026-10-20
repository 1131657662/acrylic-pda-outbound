package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.work.WorkDetailFilter
import com.acrylic.outbound.pda.work.WorkDetailRow

/** Fixed internal query vocabulary; user text only enters a bound value. */
internal data class TemporaryDetailsQuery(val filter: WorkDetailFilter, val condition: String,
    val value: Any, val descending: Boolean = false, val limit: Int = 20,
    val statusFilter: TemporaryDetailsStatusFilter? = null) {
    fun matches(row: WorkDetailRow): Boolean {
        val boundary = when (condition) {
            "sequence > ?" -> row.sequence > value as Long
            "sequence < ?" -> row.sequence < value as Long
            "sequence = ?" -> row.sequence == value
            "barcode = ?" -> row.barcode == value
            else -> error("不支持的只读明细查询")
        }
        val state = statusFilter?.matches(row) ?: when (filter) {
            WorkDetailFilter.ALL -> true
            WorkDetailFilter.SAVED -> row.status in setOf("ACTIVE", "RETAINED", "HISTORY")
            WorkDetailFilter.VOID -> row.status == "VOID"
            WorkDetailFilter.UNRESOLVED -> row.status in setOf("RECEIVED", "WAITING_CONFIRM", "CONFIRM_REQUESTED") ||
                row.status in setOf("INVALID", "BUSINESS_REJECTED") && row.failureAckAt == null
        }
        return boundary && state
    }
}
