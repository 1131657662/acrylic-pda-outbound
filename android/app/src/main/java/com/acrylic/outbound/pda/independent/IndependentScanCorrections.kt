package com.acrylic.outbound.pda.independent

import com.acrylic.outbound.pda.update.UpdateGate
import org.json.JSONObject

internal class IndependentScanCorrections(private val store: IndependentScanStore) {
    fun acknowledge(id: String) = UpdateGate.business { store.db.runInTransaction {
        val row = checkNotNull(store.dao.capture(id)); val task = store.check(row.capture.taskId)
        if (store.dao.failure(task.id)?.capture?.id != id) return@runInTransaction
        store.dao.updateState(row.processing.copy(failureAckAt = store.authorize(task).toString()))
        store.event(task.id, "INDEPENDENT_FAILURE_ACKNOWLEDGED", JSONObject().put("captureId", id).put("error", row.processing.error))
    } }
    fun discard(id: String, reason: String) = UpdateGate.business { store.db.runInTransaction {
        reason(reason); val row = checkNotNull(store.dao.capture(id)); val task = store.check(row.capture.taskId)
        check(row.capture.inputSource == "MANUAL" && row.processing.state in setOf("RECEIVED", "WAITING_CONFIRM") && row.processing.confirmedAt == null) {
            "只有尚未确认的手输输入可放弃；原输入保留"
        }
        store.dao.updateState(row.processing.copy(state = "DISCARDED", error = reason))
        store.event(task.id, "INDEPENDENT_DISCARDED", JSONObject().put("captureId", id).put("reason", reason))
    } }
    fun voidSelected(taskId: String, captureId: String, reason: String) = UpdateGate.business { store.db.runInTransaction {
        reason(reason); val task = store.check(taskId); store.requireNoOtherInput(taskId)
        check(store.dao.pending(taskId) == null && store.dao.failure(taskId) == null) { "请先处理已接收、待确认或失败输入" }
        store.verifyTotals(taskId)
        val scan = checkNotNull(store.dao.scan(captureId)); check(scan.taskId == taskId) { "本包不属于所选原独立任务" }
        store.dao.voided(captureId)?.let { check(it.reason == reason) { "原VOID原因已固定" }; return@runInTransaction }
        check(store.dao.guard(scan.barcode) == IndependentGuard(scan.barcode, captureId, taskId))
        val time = store.environment.sample(); val at = store.authorize(task).toString()
        store.dao.insertVoid(IndependentVoid(captureId, taskId, reason, time.wall, time.elapsed, time.boot, at))
        check(store.dao.release(captureId) == 1)
        val state = checkNotNull(store.db.independentTasks().state(taskId))
        store.db.independentTasks().updateState(state.copy(actualCount = Math.subtractExact(state.actualCount, 1),
            weightGrams = Math.subtractExact(state.weightGrams, scan.weightGrams)))
        store.check(taskId); store.event(taskId, "INDEPENDENT_VOID", JSONObject().put("captureId", captureId).put("reason", reason).put("authorizedAt", at))
        store.verifyTotals(taskId)
    } }
    private fun reason(value: String) {
        require(value.isNotBlank() && value.length <= 500 && Charsets.UTF_8.newEncoder().canEncode(value) &&
            value.none { Character.isISOControl(it) && it != '\n' }) { "请填写有效的实物核对及处理原因（最多500位）" }
    }
}
