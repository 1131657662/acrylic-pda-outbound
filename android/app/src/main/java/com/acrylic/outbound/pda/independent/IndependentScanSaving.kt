package com.acrylic.outbound.pda.independent

import com.acrylic.outbound.pda.update.UpdateGate
import org.json.JSONObject
import java.util.concurrent.Callable

internal class IndependentScanSaving(private val store: IndependentScanStore) {
    fun start(id: String) = UpdateGate.business { store.db.runInTransaction {
        store.check(id); store.requireNoOtherInput(id); store.verifyTotals(id)
        val state = checkNotNull(store.db.independentTasks().state(id))
        if (state.state == "SCANNING") return@runInTransaction
        check(state.state == "PLANNED")
        store.db.independentTasks().updateState(state.copy(state = "SCANNING")); store.event(id, "INDEPENDENT_STARTED")
    } }
    fun confirm(id: String) = UpdateGate.business { store.db.runInTransaction {
        val row = checkNotNull(store.dao.capture(id)); val task = store.check(row.capture.taskId)
        store.requireNoOtherInput(task.id)
        if (store.dao.failure(task.id) != null || store.dao.pending(task.id)?.capture?.id != id) return@runInTransaction
        if (row.capture.inputSource != "MANUAL" || row.processing.state != "WAITING_CONFIRM") return@runInTransaction
        val confirmed = store.authorize(task).toString()
        store.dao.updateState(row.processing.copy(state = "CONFIRM_REQUESTED", confirmedAt = confirmed))
        store.event(task.id, "INDEPENDENT_CONFIRM_REQUESTED", JSONObject().put("captureId", id).put("confirmedAt", confirmed))
    } }
    fun save(id: String): String = UpdateGate.business { store.db.runInTransaction(Callable {
        val row = checkNotNull(store.dao.capture(id)); store.check(row.capture.taskId)
        if (store.dao.scan(id) != null) return@Callable "ALREADY_SAVED"
        check(row.processing.state == "CONFIRM_REQUESTED" && row.processing.confirmedAt != null)
        check(store.dao.failure(row.capture.taskId) == null && store.dao.pending(row.capture.taskId)?.capture?.id == id)
        val error = IndependentScanValidation(store).reason(row.capture)
        if (error == null) commit(row) else reject(row, error)
    }) }
    private fun commit(row: IndependentCaptureView): String {
        val c = row.capture; val original = checkNotNull(store.row(c.barcode)); val bale = checkNotNull(original.parsedFields)
        val at = store.authorize(store.check(c.taskId)).toString(); val state = checkNotNull(store.db.independentTasks().state(c.taskId))
        store.dao.insertScan(IndependentScan(c.id, c.taskId, c.sequence, c.barcode, bale.deliveryCode, bale.model,
            bale.name, bale.rawWeight, bale.weightGrams.toLong(), original.lineNo, original.rawText, at))
        store.dao.insertGuard(IndependentGuard(c.barcode, c.id, c.taskId))
        store.db.independentTasks().updateState(state.copy(actualCount = Math.addExact(state.actualCount, 1),
            weightGrams = Math.addExact(state.weightGrams, bale.weightGrams.toLong())))
        store.dao.updateState(row.processing.copy(state = "COMMITTED")); store.check(c.taskId)
        store.event(c.taskId, "INDEPENDENT_SAVED", JSONObject().put("captureId", c.id).put("skuLineNo", original.lineNo).put("savedTrustedAt", at))
        return "SAVED"
    }
    private fun reject(row: IndependentCaptureView, error: String): String {
        store.dao.updateState(row.processing.copy(state = "BUSINESS_REJECTED", error = error))
        store.event(row.capture.taskId, "INDEPENDENT_BUSINESS_REJECTED", JSONObject().put("captureId", row.capture.id).put("error", error))
        return "BUSINESS_REJECTED"
    }
}
