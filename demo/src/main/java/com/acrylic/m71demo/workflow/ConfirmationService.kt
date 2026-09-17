package com.acrylic.m71demo.workflow

import com.acrylic.m71demo.data.*
import com.acrylic.m71demo.domain.Validation
import javax.inject.Inject

class ConfirmationService @Inject constructor(private val store: DemoStore,
    private val fault: com.acrylic.m71demo.device.FaultGate? = null) {
    fun request(id: String, pressedAtNs: Long): Boolean {
        var accepted = false
        store.db.runInTransaction {
            val capture = store.db.captures().get(id) ?: return@runInTransaction
            if (capture.state != "WAITING_CONFIRM") return@runInTransaction
            store.db.captures().updateProgress(capture.copy(state = "CONFIRM_REQUESTED", confirmedAtNs = pressedAtNs))
            store.audit(capture.context.orderId, id, AuditDetail("CONFIRM_REQUESTED",
                "${capture.context.confirmationPolicy}:${capture.context.inputSource}"))
            accepted = true
        }
        return accepted
    }

    fun save(id: String): SaveResult {
        var result = SaveResult("IGNORED", id)
        com.acrylic.m71demo.device.TransactionTiming.run(store.db, "SAVE", id) save@{
            val existing = store.db.scans().get(id)
            if (existing != null) { result = SaveResult("ALREADY_SAVED", id); return@save }
            val capture = requireNotNull(store.db.captures().get(id))
            if (capture.state !in setOf("CONFIRM_REQUESTED", "STORAGE_ERROR")) return@save
            val v = store.validation(capture)
            val error = Validation.reject(v)
            result = if (error == null) commit(v) else reject(capture, error)
        }
        return result
    }

    private fun commit(v: com.acrylic.m71demo.domain.ValidationContext): SaveResult {
        val c = v.capture
        val grams = v.bale!!.weightGrams
        val seq = store.audit(v.order.id, c.id, AuditDetail("SAVED"))
        store.db.scans().insert(ScanRecord(c.id, c.context, grams, v.bale.rawWeight,
            v.bale.deliveryCode, seq, store.clock.wallMs(), requireNotNull(c.confirmedAtNs)))
        store.db.scans().insertGuard(BarcodeGuard(c.context.barcode, v.order.id, c.id,
            requireNotNull(c.context.expectedDispatchCycle)))
        val total = Math.addExact(v.order.weightGrams, grams)
        Math.addExact(v.line!!.weightGrams, grams)
        check(store.db.orders().updateTotals(v.order.id, v.order.actualCount + 1, total) == 1)
        check(store.db.orders().addBale(v.order.id, v.line.deliveryCode, grams) == 1)
        fault?.hit("DURING_SAVE", c.id)
        store.db.captures().updateProgress(c.copy(state = "COMMITTED", confirmCommittedNs = store.clock.elapsedNs()))
        return SaveResult("SAVED", c.id, pressedAtNs = c.confirmedAtNs!!)
    }

    private fun reject(c: InputCapture, error: String): SaveResult {
        store.db.captures().updateProgress(c.copy(state = "BUSINESS_REJECTED", error = error))
        store.audit(c.context.orderId, c.id, AuditDetail("BUSINESS_REJECTED", error))
        return SaveResult("BUSINESS_REJECTED", c.id, error)
    }

    fun markStorageError(id: String) {
        store.db.runInTransaction {
            val c = store.db.captures().get(id) ?: return@runInTransaction
            if (store.db.scans().get(id) != null) return@runInTransaction
            if (c.state in setOf("CONFIRM_REQUESTED", "STORAGE_ERROR")) store.db.captures().updateProgress(c.copy(state = "STORAGE_ERROR"))
        }
    }
}

data class SaveResult(val status: String, val captureId: String, val error: String = "", val pressedAtNs: Long = 0)
