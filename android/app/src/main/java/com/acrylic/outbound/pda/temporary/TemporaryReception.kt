package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.security.TimeSample
import java.util.*
import java.util.concurrent.Callable

data class TemporaryInput(val order: String, val raw: String, val source: String, val time: TimeSample,
    val id: String = UUID.randomUUID().toString())

class TemporaryReception(private val store: TemporaryWorkStore) {
    fun receive(input: TemporaryInput): TemporaryCapture {
        require(input.raw.length <= 128) { "输入超过128字符，本条未接收，请停扫核对" }
        require(input.source in setOf("BROADCAST", "MANUAL"))
        return store.db.runInTransaction(Callable { persist(input) })
    }
    private fun persist(input: TemporaryInput): TemporaryCapture {
        val draft = store.check(input.order); store.requireNoOtherInput(input.order)
        check(store.canScan(draft)) { "请先保存计划并开始本单" }
        val device = checkNotNull(store.db.work().device(draft.creatorDeviceId))
        val catalog = store.source(input.order); val identity = catalog.manifest.getJSONObject("identity")
        val barcode = input.raw.trim(' ', '\r', '\n', '\t').uppercase(Locale.ROOT)
        val row = TemporaryCapture(input.id, input.order, draft.creatorDeviceId, draft.operatorId, draft.nextScanSeq,
            device.nextCaptureSeq, input.raw, barcode, input.source, input.time.wall, input.time.elapsed, input.time.boot,
            draft.authorizationId, identity.getString("authorizationEpoch"), catalog.manifest.getString("inventorySnapshotId"),
            identity.getString("serverRecoveryEpoch"), draft.revision, catalog.bales[barcode]?.cycle)
        store.db.temporaryScans().insertCapture(row)
        store.db.temporaryDrafts().update(draft.copy(nextScanSeq = Math.addExact(draft.nextScanSeq, 1)))
        store.db.work().updateDevice(device.copy(nextCaptureSeq = Math.addExact(device.nextCaptureSeq, 1)))
        store.event(draft, row.id, "RECEIVED", input.source); store.check(input.order)
        return row
    }
    fun validate(id: String): TemporaryCapture = store.db.runInTransaction(Callable {
        val row = checkNotNull(store.db.temporaryScans().capture(id))
        if (row.state != "RECEIVED") return@Callable row
        val error = TemporaryScanValidation(store).reason(row)
        val state = if (error != null) "INVALID" else if (row.inputSource == "BROADCAST") "CONFIRM_REQUESTED" else "WAITING_CONFIRM"
        row.copy(state = state, error = error.orEmpty(), confirmedAt = if (state == "CONFIRM_REQUESTED") store.connection.now().toString() else null).also {
            store.db.temporaryScans().updateCapture(it)
            store.event(store.check(row.clientOrderId), row.id, state, it.error)
        }
    })
}
