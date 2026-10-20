package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.work.WorkDetailRow

/** Read-only status choices for the temporary order's original-record view. */
enum class TemporaryDetailsStatusFilter(val label: String, internal val predicate: String) {
    ALL("全部", "1=1"),
    ACTIVE("有效", "status='ACTIVE'"),
    VOID("已撤销", "status='VOID'"),
    RETAINED("主管保留", "status='RETAINED'");

    internal fun matches(row: WorkDetailRow): Boolean = when (this) {
        ALL -> true
        ACTIVE -> row.status == "ACTIVE"
        VOID -> row.status == "VOID"
        RETAINED -> row.status == "RETAINED"
    }
}
