package com.acrylic.outbound.pda.temporary

import androidx.sqlite.db.SimpleSQLiteQuery
import com.acrylic.outbound.pda.work.*
import java.util.Locale
import java.util.concurrent.Callable

data class TemporaryDetailsPage(val order: TemporaryDraft, val products: List<TemporaryLine>,
    val totals: List<ScanProductTotal>, val total: ScanTotal, val rows: List<WorkDetailRow>,
    val previous: Boolean, val next: Boolean)

/** One original identity, one verified predecessor, one bounded read-only transaction snapshot. */
class TemporaryDetailsReader(private val store: TemporaryWorkStore, private val id: String) {
    private val prefix = TemporaryDetailsPrefix(store)
    fun page(filter: WorkDetailFilter, anchor: Long = 0, backwards: Boolean = false): TemporaryDetailsPage =
        page(filter, null, anchor, backwards)

    fun page(filter: TemporaryDetailsStatusFilter = TemporaryDetailsStatusFilter.ALL,
        anchor: Long = 0, backwards: Boolean = false): TemporaryDetailsPage =
        page(WorkDetailFilter.ALL, filter, anchor, backwards)

    private fun page(filter: WorkDetailFilter, statusFilter: TemporaryDetailsStatusFilter?,
        anchor: Long, backwards: Boolean): TemporaryDetailsPage = snapshot {
        val rows = query(it, TemporaryDetailsQuery(filter, if (backwards) "sequence < ?" else "sequence > ?",
            anchor, backwards, statusFilter = statusFilter))
            .let { found -> if (backwards) found.reversed() else found }
        val previous = rows.firstOrNull()?.let { row ->
            query(it, TemporaryDetailsQuery(filter, "sequence < ?", row.sequence, true, 1, statusFilter)).isNotEmpty()
        } ?: false
        val next = rows.lastOrNull()?.let { row ->
            query(it, TemporaryDetailsQuery(filter, "sequence > ?", row.sequence, limit = 1, statusFilter = statusFilter)).isNotEmpty()
        } ?: false
        val totals = TemporaryDetailsTotals(store, id); val products = totals.products(it)
        TemporaryDetailsPage(store.check(id, false), store.db.temporaryDrafts().lines(id), products,
            totals.total(products), rows, previous, next)
    }

    fun locate(raw: String, byBarcode: Boolean): WorkDetailRow? = snapshot {
        val text = raw.trim().uppercase(Locale.ROOT)
        val value: Any = if (byBarcode) {
            require(text.matches(Regex("[0-9A-Z]{11}"))) { "请输入11位完整条码" }; text
        } else text.toLongOrNull()?.takeIf { n -> n > 0 } ?: error("请输入有效原序号")
        query(it, TemporaryDetailsQuery(WorkDetailFilter.ALL, if (byBarcode) "barcode = ?" else "sequence = ?", value, true, 1)).firstOrNull()
    }

    private fun <T> snapshot(read: (TemporaryDetailsOriginal?) -> T): T = store.db.runInTransaction(Callable {
        val draft = store.load(id); store.check(id, false)
        read(prefix.original(draft)).also { store.check(id, false) }
    })

    private fun query(original: TemporaryDetailsOriginal?, q: TemporaryDetailsQuery): List<WorkDetailRow> {
        val direction = if (q.descending) "DESC" else "ASC"
        val sql = """SELECT * FROM (SELECT c.id AS captureId,c.orderScanSeq AS sequence,c.barcode,
            COALESCE(s.status,c.state) AS status,s.deliveryCode,s.model,s.name,s.weightGrams,c.error,c.failureAckAt
            FROM temporary_capture c LEFT JOIN temporary_scan s ON s.captureId=c.id WHERE c.clientOrderId=?)
            WHERE (${q.statusFilter?.predicate ?: q.filter.predicate}) AND ${q.condition} ORDER BY sequence $direction LIMIT ?"""
        val current = store.db.temporaryScans().detailRows(SimpleSQLiteQuery(sql, arrayOf(id, q.value, q.limit)))
        val combined = (prefix.page(original, q) + current).sortedBy { it.sequence }
        return (if (q.descending) combined.asReversed() else combined).take(q.limit)
    }
}
