package com.acrylic.outbound.pda.ui

import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.work.*
import java.util.Locale

data class AdjustmentRow(val capture: String, val sequence: Long, val barcode: String, val product: String,
    val grams: Long, val status: String) {
    val label get() = "$sequence. $barcode\n$product · ${WorkRenderer.kg(grams)} kg · ${when (status) {
        "VOID" -> "已撤销"; "RETAINED" -> "主管核验保留，须受控处置"; else -> "已保存" }}"
}

/** Reads original local facts; only the application writer can perform a correction. */
class AdjustmentRecords(private val app: PdaApplication, private val assignment: String, private val temporary: String) {
    private val formal by lazy { WorkStore(app.database, SessionWorkAccess(app, app.session)) }
    private val temp by lazy { app.temporaryStore() }
    init { require(assignment.isNotBlank() != temporary.isNotBlank()) }

    fun title(): String = app.database.runInTransaction(java.util.concurrent.Callable {
        identity()
        if (assignment.isNotBlank()) "正式单 · ${checkNotNull(app.database.work().order(assignment)).vehicleNo}"
        else "临时单 · ${checkNotNull(app.database.temporaryDrafts().get(temporary)).vehicleNo}"
    })
    fun page(after: Long): List<AdjustmentRow> = app.database.runInTransaction(java.util.concurrent.Callable {
        identity()
        if (assignment.isNotBlank()) app.database.scans().page(assignment, after, 20).map(::formalRow)
        else temp.page(temporary, after).take(20).map { AdjustmentRow(it.captureId, it.orderScanSeq, it.barcode,
            "${it.deliveryCode} · ${it.model} · ${it.name}", it.weightGrams,
            if (app.database.temporaryScans().scan(it.captureId) == null) "RETAINED" else it.status) }
    })
    fun locate(raw: String): AdjustmentRow? = app.database.runInTransaction(java.util.concurrent.Callable {
        identity(); val barcode = raw.trim(' ', '\r', '\n', '\t').uppercase(Locale.ROOT)
        require(barcode.isNotBlank() && barcode.length <= 128) { "请输入完整条码" }
        if (assignment.isNotBlank()) app.database.scans().locate(assignment, barcode)?.let(::formalRow)
        else app.database.temporaryScans().locate(temporary, barcode)?.let { AdjustmentRow(it.captureId, it.orderScanSeq,
            it.barcode, "${it.deliveryCode} · ${it.model} · ${it.name}", it.weightGrams, it.status) }
    })
    suspend fun adjust(row: AdjustmentRow, reason: String) {
        if (assignment.isNotBlank()) app.work.adjust(assignment, row.capture, reason).await()
        else app.temporaryWork.adjust(temporary, row.capture, reason).await()
    }
    fun blockReason(): String = runCatching {
        identity()
        if (assignment.isNotBlank()) {
            check(app.database.work().order(assignment)?.state == "SCANNING") { "本单已冻结或停止，只可核对原记录" }
            formal.access.authorize(formal.prepared(assignment))
            check(app.work.state.value.queued == 0 && app.database.captures().pendingCount(assignment) == 0 &&
                app.database.captures().failure(assignment) == null) { "请先处理本单未决输入并等待落盘" }
            check(app.database.captures().unresolvedOther(assignment) == null && app.database.temporaryScans().unresolved() == 0) { "请先处理另一订单未决输入" }
        } else {
            val draft = temp.check(temporary)
            check(temp.canScan(draft)) { "本单已冻结或停止，只可核对原记录" }
            temp.requireNoOtherInput(temporary)
            check(app.temporaryWork.state.value.queued == 0 && app.database.temporaryScans().pending(temporary) == null &&
                app.database.temporaryScans().failure(temporary) == null) { "请先处理本单未决输入并等待落盘" }
        }
    }.exceptionOrNull()?.message.orEmpty()
    private fun identity() {
        if (assignment.isNotBlank()) formal.access.identity(formal.prepared(assignment))
        else { temp.load(temporary); temp.check(temporary, false) }
    }
    private fun formalRow(row: WorkScan): AdjustmentRow {
        val boundary = app.database.continuations().current(assignment)?.nextOrderScanSeq ?: 0
        val retained = row.orderScanSeq < boundary || app.database.dispositions().all(assignment).any { it.captureId == row.captureId }
        return AdjustmentRow(row.captureId, row.orderScanSeq, row.barcode,
            "${row.deliveryCode} · ${row.model} · ${row.name}", row.weightGrams, if (retained) "RETAINED" else row.status)
    }
}
