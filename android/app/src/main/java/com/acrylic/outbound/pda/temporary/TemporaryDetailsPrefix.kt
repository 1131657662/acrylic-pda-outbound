package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.work.WorkDetailRow
import org.json.JSONObject

/** Projects only already verified original predecessor bytes; it never installs or activates work. */
internal data class TemporaryDetailsOriginal(val body: JSONObject, val retained: List<JSONObject>, val historyOnly: Boolean = false)

internal class TemporaryDetailsPrefix(private val store: TemporaryWorkStore) {
    fun original(draft: TemporaryDraft): TemporaryDetailsOriginal? = when {
        store.isHandover(draft.clientOrderId) -> TemporaryHandoverBaseline(store).read(draft).let {
            TemporaryDetailsOriginal(it.original, it.retained, true)
        }
        draft.workRound > 1 -> TemporaryContinuationBaseline(store).read(draft, draft.state != "CONTINUATION_BLOCKED")
            ?.original?.let { original ->
                val rows = original.getJSONArray("scans")
                TemporaryDetailsOriginal(original, (0 until rows.length()).map { rows.getJSONObject(it) }.filter { it.getString("status") == "ACTIVE" })
            }
        else -> null
    }

    fun page(original: TemporaryDetailsOriginal?, query: TemporaryDetailsQuery): List<WorkDetailRow> {
        if (original == null) return emptyList()
        val captures = original.body.getJSONArray("captures"); val scans = original.body.getJSONArray("scans")
        val saved = (0 until scans.length()).associate { scans.getJSONObject(it).let { row -> row.getString("captureId") to row } }
        val indices = if (query.descending) (captures.length() - 1 downTo 0) else (0 until captures.length())
        return indices.asSequence().map { captures.getJSONObject(it) }.map { capture ->
            project(capture, saved[capture.getString("id")], original.historyOnly)
        }.filter(query::matches).take(query.limit).toList()
    }

    private fun project(capture: JSONObject, scan: JSONObject?, historyOnly: Boolean): WorkDetailRow {
        val status = scan?.getString("status")?.let {
            if (it == "ACTIVE") { if (historyOnly) "HISTORY" else "RETAINED" } else it
        } ?: capture.getString("state")
        return WorkDetailRow(capture.getString("id"), capture.getString("orderScanSeq").toLong(),
            capture.getString("barcode"), status, scan?.getString("deliveryCode"), scan?.getString("model"),
            scan?.getString("name"), scan?.getString("weightGrams")?.toLong(), capture.optString("error", ""),
            capture.takeUnless { it.isNull("failureAckAt") }?.optString("failureAckAt")?.takeIf { it.isNotBlank() })
    }
}
