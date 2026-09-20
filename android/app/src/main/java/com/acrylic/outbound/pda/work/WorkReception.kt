package com.acrylic.outbound.pda.work

import com.acrylic.outbound.pda.security.TimeSample
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

data class WorkInput(val assignmentId: String, val raw: String, val source: String, val time: TimeSample,
    val id: String = UUID.randomUUID().toString())

class WorkReception(private val store: WorkStore) {
    fun receive(input: WorkInput): WorkCapture {
        require(input.raw.length <= 128) { "输入超过128字符，停止接收；本条未持久化，请核对" }
        require(input.source in setOf("BROADCAST", "MANUAL"))
        lateinit var result: WorkCapture
        store.db.runInTransaction { result = persist(input) }
        return result
    }

    private fun persist(input: WorkInput): WorkCapture {
        val prepared = store.prepared(input.assignmentId); store.access.authorize(prepared)
        check(store.db.temporaryScans().unresolved() == 0) { "临时单仍有未处理输入，请先恢复原单" }
        val work = checkNotNull(store.db.work().order(input.assignmentId))
        check(work.state == "SCANNING" && work.nextOrderScanSeq < Long.MAX_VALUE)
        val device = checkNotNull(store.db.work().device(prepared.deviceId))
        val barcode = input.raw.trim(' ', '\r', '\n', '\t').uppercase(Locale.ROOT)
        val assignment = JSONObject(prepared.assignmentJson)
        val claims = JSONObject(String(java.util.Base64.getUrlDecoder().decode(assignment.getString("offlineToken").split('.')[1]), Charsets.UTF_8))
        val context = CaptureContext(input.assignmentId, prepared.orderId, prepared.deviceId, prepared.operatorId,
            claims.getString("warehouseId"), work.nextOrderScanSeq, device.nextCaptureSeq, input.raw, barcode, input.source,
            if (input.source == "BROADCAST") "AUTO_AFTER_VALIDATION" else "MANUAL", input.time.wall, input.time.elapsed, input.time.boot,
            assignment.getString("inventorySnapshotId"), store.db.preparations().bale(input.assignmentId, barcode)?.dispatchCycle,
            assignment.getString("serverRecoveryEpoch"), assignment.getString("offlineAuthorizationId"), assignment.getString("epoch"), assignment.getInt("revision"), originAssignmentId = assignment.getString("id"))
        val capture = WorkCapture(input.id, context); store.db.captures().insert(capture)
        check(store.db.work().advanceOrder(input.assignmentId, work.nextOrderScanSeq) == 1)
        store.db.work().updateDevice(device.copy(nextCaptureSeq = Math.addExact(device.nextCaptureSeq, 1)))
        store.event(input.assignmentId, input.id, "RECEIVED", context.confirmationPolicy)
        return capture
    }

    fun validate(id: String): WorkCapture {
        lateinit var result: WorkCapture
        store.db.runInTransaction {
            val c = checkNotNull(store.db.captures().get(id))
            check(store.db.work().order(c.context.assignmentId)?.state == "SCANNING") { "本单已停止，请核对召回" }
            if (c.state != "RECEIVED") { result = c; return@runInTransaction }
            val validation = WorkValidation(store).validate(c)
            val automatic = c.context.confirmationPolicy == "AUTO_AFTER_VALIDATION"
            val state = if (validation.error != null) "INVALID" else if (automatic) "CONFIRM_REQUESTED" else "WAITING_CONFIRM"
            val confirmed = if (state == "CONFIRM_REQUESTED") store.access.authorize(store.prepared(c.context.assignmentId)).toString() else null
            result = c.copy(state = state, error = validation.error.orEmpty(), confirmedAt = confirmed)
            check(store.db.captures().progress(id, state, result.error, confirmed) == 1)
            store.event(c.context.assignmentId, id, state, result.error.ifEmpty { c.context.confirmationPolicy })
        }
        return result
    }
}
