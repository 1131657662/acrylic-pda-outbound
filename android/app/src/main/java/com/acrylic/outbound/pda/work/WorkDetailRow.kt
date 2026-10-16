package com.acrylic.outbound.pda.work

/** Read-only original capture projection; no reconstructed or renumbered records. */
data class WorkDetailRow(val captureId: String, val sequence: Long, val barcode: String,
    val status: String, val deliveryCode: String?, val model: String?, val name: String?,
    val weightGrams: Long?, val error: String, val failureAckAt: String?)

enum class WorkDetailFilter(val label: String, internal val predicate: String) {
    ALL("全部原记录", "1=1"),
    SAVED("已保存原记录", "status IN ('ACTIVE','RETAINED')"),
    VOID("已撤销", "status='VOID'"),
    UNRESOLVED("未处理输入", "status IN ('RECEIVED','WAITING_CONFIRM','CONFIRM_REQUESTED') OR (status IN ('INVALID','BUSINESS_REJECTED') AND failureAckAt IS NULL)")
}

data class WorkDetailsPage(val order: WorkOrder, val products: List<WorkLine>,
    val rows: List<WorkDetailRow>, val previous: Boolean, val next: Boolean)
