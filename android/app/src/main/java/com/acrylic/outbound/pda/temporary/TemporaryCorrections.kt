package com.acrylic.outbound.pda.temporary

import java.time.Instant

class TemporaryCorrections(private val store: TemporaryWorkStore) {
    fun acknowledge(id: String) = store.db.runInTransaction {
        val row = checkNotNull(store.db.temporaryScans().capture(id)); val draft = store.check(row.clientOrderId, false)
        check(store.canScan(draft))
        if (store.db.temporaryScans().failure(row.clientOrderId)?.id != id) return@runInTransaction
        store.db.temporaryScans().updateCapture(row.copy(failureAckAt = Instant.ofEpochMilli(store.sample().wall).toString()))
        store.event(draft, id, "FAILURE_ACKNOWLEDGED", row.error)
    }
    fun discard(id: String, reason: String) = store.db.runInTransaction {
        require(reason.isNotBlank() && reason.length <= 500)
        val row = checkNotNull(store.db.temporaryScans().capture(id)); val draft = store.check(row.clientOrderId, false)
        check(store.canScan(draft) && row.inputSource == "MANUAL" && row.state in setOf("RECEIVED", "WAITING_CONFIRM") && row.confirmedAt == null) { "只能放弃未确认的手输项" }
        store.db.temporaryScans().updateCapture(row.copy(state = "DISCARDED", error = reason))
        store.event(draft, id, "DISCARDED", reason)
    }
    fun voidLast(order: String, capture: String, reason: String) = store.db.runInTransaction {
        check(store.db.temporaryScans().last(order)?.captureId == capture) { "末包已变化，请重新核对" }
        voidSelected(order, capture, reason)
    }
    fun voidSelected(order: String, capture: String, reason: String) = store.db.runInTransaction {
        require(reason.isNotBlank() && reason.length <= 500) { "请填写实物核对及撤销原因" }
        val draft = store.check(order); store.requireNoOtherInput(order)
        check(store.canScan(draft) && store.db.temporaryScans().pending(order) == null && store.db.temporaryScans().failure(order) == null) { "请先处理未决输入" }
        val scan = checkNotNull(store.db.temporaryScans().scan(capture)) { "没有可撤销明细" }
        check(scan.clientOrderId == order && scan.status == "ACTIVE") { "本包不属于原临时单或已撤销，请重新核对" }
        check(store.db.temporaryScans().markVoid(capture) == 1 && store.db.temporaryScans().release(capture) == 1)
        store.event(draft, capture, "VOID", reason); store.check(order)
    }
}
