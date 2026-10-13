package com.acrylic.outbound.pda.independent

import com.acrylic.outbound.pda.security.TrustedTime
import com.acrylic.outbound.pda.update.UpdateGate
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Callable

internal class IndependentScanReception(private val store: IndependentScanStore) {
    fun receive(input: IndependentInput): IndependentCaptureView = UpdateGate.business {
        require(input.raw.length <= 128 && Charsets.UTF_8.newEncoder().canEncode(input.raw)) { "输入过长或编码异常，本条未接收，请停扫核对" }
        require(input.source in setOf("BROADCAST", "MANUAL") && UUID.fromString(input.id).toString() == input.id)
        store.db.runInTransaction(Callable { persist(input) })
    }
    private fun persist(input: IndependentInput): IndependentCaptureView {
        val task = store.check(input.taskId); store.requireNoOtherInput(task.id)
        check(store.db.independentTasks().state(task.id)?.state == "SCANNING") { "请先核验口令并开始独立作业" }
        store.dao.capture(input.id)?.let { old ->
            val c = old.capture
            check(c.taskId == input.taskId && c.rawInput == input.raw && c.inputSource == input.source &&
                c.wall == input.time.wall && c.elapsed == input.time.elapsed && c.boot == input.time.boot) { "原输入键已固定，不能替换正文" }
            return old
        }
        val now = store.environment.sample()
        check(input.time.boot == now.boot && input.time.elapsed <= now.elapsed && input.time.elapsed >= task.createdElapsed)
        val capturedAt = TrustedTime.now(JSONObject(task.timeAnchorJson), input.time)
        IndependentCredentialProof.verify(task.signedCredential, JSONObject(task.trustedKeyJson)).at(capturedAt)
        val state = checkNotNull(store.db.independentTasks().state(task.id))
        store.dao.insertDevice(IndependentDeviceState(task.deviceId)); val device = checkNotNull(store.dao.device(task.deviceId))
        val capture = IndependentCapture(input.id, task.id, state.nextScanSeq, task.deviceId, device.nextCaptureSeq,
            input.raw, input.raw.trim(' ', '\r', '\n', '\t').uppercase(Locale.ROOT), input.source,
            input.time.wall, input.time.elapsed, input.time.boot, capturedAt.toString())
        store.dao.insertCapture(capture); store.dao.insertState(IndependentCaptureState(capture.id))
        store.dao.updateDevice(device.copy(nextCaptureSeq = Math.addExact(device.nextCaptureSeq, 1)))
        store.db.independentTasks().updateState(state.copy(nextScanSeq = Math.addExact(state.nextScanSeq, 1)))
        store.check(task.id); store.event(task.id, "INDEPENDENT_RECEIVED", JSONObject().put("captureId", capture.id).put("source", input.source))
        return checkNotNull(store.dao.capture(capture.id))
    }
    fun validate(id: String): IndependentCaptureView = UpdateGate.business { store.db.runInTransaction(Callable {
        val row = checkNotNull(store.dao.capture(id)); store.check(row.capture.taskId)
        if (row.processing.state != "RECEIVED") return@Callable row
        val error = IndependentScanValidation(store).reason(row.capture)
        val state = if (error != null) "INVALID" else if (row.capture.inputSource == "BROADCAST") "CONFIRM_REQUESTED" else "WAITING_CONFIRM"
        val updated = row.processing.copy(state = state, error = error.orEmpty(),
            confirmedAt = if (state == "CONFIRM_REQUESTED") store.authorize(store.owner(row.capture.taskId)).toString() else null)
        store.dao.updateState(updated); store.event(row.capture.taskId, "INDEPENDENT_$state", JSONObject().put("captureId", id).put("error", updated.error))
        IndependentCaptureView(row.capture, updated)
    }) }
}
