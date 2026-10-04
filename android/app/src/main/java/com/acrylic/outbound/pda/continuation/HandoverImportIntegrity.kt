package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.data.PdaDatabase

/** Rechecks the imported rows on every retry without replacing existing facts. */
internal class HandoverImportIntegrity(private val db: PdaDatabase) {
    fun verify(work: HandoverImportedWork, rows: HandoverRecoveryRows) {
        val id = work.order.assignmentId
        check(db.work().order(id) == work.order && db.work().lines(id) == work.lines) { "交接本地汇总已变化" }
        check(db.captures().page(id, 0, Int.MAX_VALUE) == rows.captures) { "交接原输入已变化" }
        check(db.scans().page(id, 0, Int.MAX_VALUE) == rows.scans) { "交接原保存记录已变化" }
        check(db.work().events(id) == rows.events) { "交接原审计已变化" }
        check(db.dispositions().all(id) == rows.dispositions) { "交接原处置已变化" }
        check(db.work().guards(id) == work.guards.sortedBy { it.barcode }) { "交接原包防重已变化" }
        check(db.work().device(work.targetDevice) != null) { "目标设备序号缺失" }
        val totals = db.scans().total(id)
        check(totals.count == work.order.actualCount && totals.grams == work.order.weightGrams) { "交接明细汇总不符" }
    }
}
