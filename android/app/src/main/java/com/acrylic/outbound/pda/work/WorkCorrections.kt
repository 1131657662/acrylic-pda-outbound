package com.acrylic.outbound.pda.work

class WorkCorrections(private val store: WorkStore) {
    fun acknowledge(id: String) {
        store.db.runInTransaction {
            val c = store.db.captures().get(id) ?: return@runInTransaction
            store.access.identity(store.prepared(c.context.assignmentId))
            check(store.db.work().order(c.context.assignmentId)?.state == "SCANNING") { "订单已停止，请进入召回核对" }
            if (store.db.captures().failure(c.context.assignmentId)?.id != id) return@runInTransaction
            val at = java.time.Instant.ofEpochMilli(store.access.sample().wall).toString()
            if (store.db.captures().acknowledge(id, at) == 1) store.event(c.context.assignmentId, id, "FAILURE_ACKNOWLEDGED", c.error)
        }
    }

    fun discard(id: String, reason: String) {
        require(reason.isNotBlank() && reason.length <= 500)
        store.db.runInTransaction {
            val c = store.db.captures().get(id) ?: return@runInTransaction
            store.access.identity(store.prepared(c.context.assignmentId))
            check(store.db.work().order(c.context.assignmentId)?.state == "SCANNING") { "订单已停止，请进入召回核对" }
            check(c.context.confirmationPolicy == "MANUAL" && c.state in setOf("RECEIVED", "WAITING_CONFIRM") && c.confirmedAt == null) { "只有尚未确认的手输项可以放弃" }
            check(store.db.captures().progress(id, "DISCARDED", reason, null) == 1)
            store.event(c.context.assignmentId, id, "DISCARDED", reason)
        }
    }

    fun voidLast(assignment: String, capture: String, reason: String) {
        require(reason.isNotBlank() && reason.length <= 500) { "请填写实物核对及撤销原因" }
        store.db.runInTransaction {
            store.access.authorize(store.prepared(assignment))
            val order = checkNotNull(store.db.work().order(assignment))
            check(order.state == "SCANNING" && store.db.captures().pendingCount(assignment) == 0 && store.db.captures().failure(assignment) == null) { "请先处理未决输入" }
            val scan = checkNotNull(store.db.scans().lastActive(assignment)) { "没有可撤销明细" }
            check(store.db.continuations().current(assignment)?.let { scan.orderScanSeq >= it.nextOrderScanSeq } != false) { "本包属于主管核验保留明细，卸货须另行批准" }
            check(scan.captureId == capture) { "末包已变化，请重新核对" }
            val line = checkNotNull(store.db.work().line(assignment, scan.deliveryCode))
            check(store.db.scans().markVoid(capture) == 1 && store.db.scans().release(capture) == 1)
            check(store.db.work().totals(assignment, order.actualCount - 1, Math.subtractExact(order.weightGrams, scan.weightGrams), null) == 1)
            check(store.db.work().lineTotals(assignment, line.deliveryCode, line.actualCount - 1, Math.subtractExact(line.weightGrams, scan.weightGrams)) == 1)
            store.event(assignment, capture, "VOID", reason)
        }
    }
}
