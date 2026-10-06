package com.acrylic.outbound.pda.temporary

import java.util.concurrent.Callable

class TemporarySaving(private val store: TemporaryWorkStore) {
    fun confirm(id: String) = store.db.runInTransaction {
        val row = checkNotNull(store.db.temporaryScans().capture(id)); val draft = store.check(row.clientOrderId)
        check(draft.canScan())
        if (row.inputSource != "MANUAL" || row.state != "WAITING_CONFIRM") return@runInTransaction
        check(store.db.temporaryScans().pending(row.clientOrderId)?.id == id && store.db.temporaryScans().failure(row.clientOrderId) == null)
        store.db.temporaryScans().updateCapture(row.copy(state = "CONFIRM_REQUESTED", confirmedAt = store.connection.now().toString()))
        store.event(draft, id, "CONFIRM_REQUESTED", "MANUAL")
    }
    fun save(id: String): String = store.db.runInTransaction(Callable {
        if (store.db.temporaryScans().scan(id) != null) return@Callable "ALREADY_SAVED"
        val row = checkNotNull(store.db.temporaryScans().capture(id))
        check(row.state == "CONFIRM_REQUESTED" && row.confirmedAt != null)
        check(store.db.temporaryScans().pending(row.clientOrderId)?.id == id && store.db.temporaryScans().failure(row.clientOrderId) == null)
        val reason = TemporaryScanValidation(store).reason(row)
        if (reason == null) commit(row) else {
            store.db.temporaryScans().updateCapture(row.copy(state = "BUSINESS_REJECTED", error = reason))
            store.event(store.check(row.clientOrderId), id, "BUSINESS_REJECTED", reason); "BUSINESS_REJECTED"
        }
    })
    private fun commit(row: TemporaryCapture): String {
        val draft = store.check(row.clientOrderId); val bale = store.source(row.clientOrderId).bales.getValue(row.barcode)
        // Overflow is a storage failure: leave the durable confirmation pending and roll back every fact.
        Math.addExact(store.db.temporaryScans().total(row.clientOrderId).grams, bale.grams)
        val sequence = store.event(draft, row.id, "SAVED")
        store.db.temporaryScans().insertScan(TemporaryScan(row.id, row.clientOrderId, row.orderScanSeq, bale.barcode, bale.baleId,
            bale.code, bale.model, bale.name, bale.rawWeight, bale.grams, bale.cycle, store.connection.now().toString(), sequence))
        store.db.temporaryScans().insertGuard(TemporaryGuard(bale.barcode, row.id))
        store.db.temporaryScans().updateCapture(row.copy(state = "COMMITTED")); store.check(row.clientOrderId)
        return "SAVED"
    }
}
