package com.acrylic.outbound.pda.work

class WorkSaving(private val store: WorkStore) {
    fun confirm(id: String) {
        store.db.runInTransaction {
            val c = store.db.captures().get(id) ?: return@runInTransaction
            val assignment = c.context.assignmentId
            if (store.db.captures().failure(assignment) != null || store.db.captures().pending(assignment)?.id != id) return@runInTransaction
            if (c.state != "WAITING_CONFIRM" || c.context.confirmationPolicy != "MANUAL") return@runInTransaction
            val time = store.access.authorize(store.prepared(assignment)).toString()
            check(store.db.captures().progress(id, "CONFIRM_REQUESTED", "", time) == 1)
            store.event(assignment, id, "CONFIRM_REQUESTED", "MANUAL")
        }
    }

    fun save(id: String): String {
        var result = "ALREADY_SAVED"
        store.db.runInTransaction {
            if (store.db.scans().get(id) != null) return@runInTransaction
            val c = checkNotNull(store.db.captures().get(id))
            check(c.state == "CONFIRM_REQUESTED" && c.confirmedAt != null)
            val assignment = c.context.assignmentId
            check(store.db.captures().failure(assignment) == null && store.db.captures().pending(assignment)?.id == id)
            val validation = WorkValidation(store).validate(c)
            result = if (validation.error == null) commit(validation) else reject(c, validation.error)
        }
        return result
    }

    private fun commit(v: ValidatedCapture): String {
        val c = v.capture; val bale = checkNotNull(v.bale); val line = checkNotNull(v.line); val id = c.context.assignmentId
        val savedAt = store.access.authorize(store.prepared(id)).toString()
        val total = Math.addExact(v.order.weightGrams, bale.weightGrams); val grams = Math.addExact(line.weightGrams, bale.weightGrams)
        val seq = store.event(id, c.id, "SAVED")
        store.db.scans().insert(WorkScan(c.id, id, c.context.orderScanSeq, bale.barcode, bale.baleId,
            bale.deliveryCode, bale.model, bale.name, bale.rawWeight, bale.weightGrams, checkNotNull(c.context.expectedDispatchCycle), savedAt, seq))
        store.db.scans().insertGuard(WorkGuard(bale.barcode, c.id, id, bale.dispatchCycle))
        check(store.db.work().totals(id, Math.addExact(v.order.actualCount, 1), total, savedAt) == 1)
        check(store.db.work().lineTotals(id, line.deliveryCode, Math.addExact(line.actualCount, 1), grams) == 1)
        check(store.db.captures().progress(c.id, "COMMITTED", "", c.confirmedAt) == 1)
        return "SAVED"
    }

    private fun reject(c: WorkCapture, error: String): String {
        check(store.db.captures().progress(c.id, "BUSINESS_REJECTED", error, c.confirmedAt) == 1)
        store.event(c.context.assignmentId, c.id, "BUSINESS_REJECTED", error)
        return "BUSINESS_REJECTED"
    }
}
