package com.acrylic.outbound.pda.ui

import com.acrylic.outbound.pda.work.WorkDetailRow

/** Historical predecessor visibility does not claim a new valid physical disposition. */
object TemporaryDetailsText {
    fun row(row: WorkDetailRow): String = if (row.status == "HISTORY") {
        "原序号 ${row.sequence} · ${row.barcode}\n已保存前驱原记录 · 只读核对，实物状态见主管决定\n" +
            "${row.deliveryCode.orEmpty()} · ${row.model.orEmpty()} · ${row.name.orEmpty()}" +
            row.weightGrams?.let { " · ${WorkRenderer.kg(it)} kg" }.orEmpty()
    } else WorkDetailsText.row(row)
}
