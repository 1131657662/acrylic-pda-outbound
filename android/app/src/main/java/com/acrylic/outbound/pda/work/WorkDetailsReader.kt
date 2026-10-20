package com.acrylic.outbound.pda.work

import androidx.sqlite.db.SimpleSQLiteQuery
import com.acrylic.outbound.pda.data.PdaDatabase
import java.util.Locale
import java.util.concurrent.Callable

/** Bounded, identity-checked snapshot of one formal order's original local facts. */
class WorkDetailsReader(private val db: PdaDatabase, private val id: String,
    private val identity: () -> Unit) {
    fun page(filter: WorkDetailFilter, anchor: Long = 0, backwards: Boolean = false): WorkDetailsPage =
        db.runInTransaction(Callable {
            identity()
            val order = checkNotNull(db.work().order(id)) { "本机没有此单原记录" }
            val prepared = checkNotNull(db.preparations().order(id)) { "本机缺少此单原计划，请保留原记录核对" }
            val number = FormalOrderNumber.read(prepared, order)
            val rows = query(filter, "sequence ${if (backwards) "<" else ">"} ?", anchor,
                if (backwards) "DESC" else "ASC", PAGE_SIZE).let { if (backwards) it.reversed() else it }
            val previous = rows.firstOrNull()?.let { query(filter, "sequence < ?", it.sequence, "DESC", 1).isNotEmpty() } ?: false
            val next = rows.lastOrNull()?.let { query(filter, "sequence > ?", it.sequence, "ASC", 1).isNotEmpty() } ?: false
            WorkDetailsPage(order, db.work().lines(id), rows, previous, next, number).also { identity() }
        })

    fun locate(raw: String, byBarcode: Boolean): WorkDetailRow? = db.runInTransaction(Callable {
        identity()
        val text = raw.trim().uppercase(Locale.ROOT)
        val value: Any = if (byBarcode) {
            require(text.matches(Regex("[0-9A-Z]{11}"))) { "请输入11位完整条码" }; text
        } else text.toLongOrNull()?.takeIf { it > 0 } ?: error("请输入有效原序号")
        query(WorkDetailFilter.ALL, if (byBarcode) "barcode = ?" else "sequence = ?", value, "DESC", 1)
            .firstOrNull().also { identity() }
    })

    private fun query(filter: WorkDetailFilter, condition: String, value: Any, direction: String, limit: Int): List<WorkDetailRow> {
        val boundary = db.continuations().current(id)?.nextOrderScanSeq ?: 0L
        val sql = """SELECT * FROM (SELECT c.id AS captureId,c.orderScanSeq AS sequence,c.barcode,
            CASE WHEN s.captureId IS NULL THEN c.state WHEN s.status='VOID' THEN 'VOID'
              WHEN s.orderScanSeq < ? OR EXISTS(SELECT 1 FROM local_disposition d WHERE d.captureId=s.captureId)
              THEN 'RETAINED' ELSE s.status END AS status,
            s.deliveryCode,s.model,s.name,s.weightGrams,c.error,c.failureAckAt
            FROM work_capture c LEFT JOIN work_scan s ON s.captureId=c.id WHERE c.assignmentId=?)
            WHERE (${filter.predicate}) AND $condition ORDER BY sequence $direction LIMIT ?"""
        return db.scans().detailRows(SimpleSQLiteQuery(sql, arrayOf(boundary, id, value, limit)))
    }

    companion object { const val PAGE_SIZE = 20 }
}
