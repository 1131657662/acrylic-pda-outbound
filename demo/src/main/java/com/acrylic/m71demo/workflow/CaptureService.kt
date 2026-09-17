package com.acrylic.m71demo.workflow

import com.acrylic.m71demo.data.*
import com.acrylic.m71demo.domain.Validation
import javax.inject.Inject

class CaptureService @Inject constructor(private val store: DemoStore) {
    fun receive(input: IncomingBarcode): InputCapture {
        require(input.raw.length <= 128) { "输入超过128字符，已停扫，请核对" }
        lateinit var result: InputCapture
        com.acrylic.m71demo.device.TransactionTiming.run(store.db, "RECEIVE", input.id) { result = persist(input) }
        return result
    }

    private fun persist(input: IncomingBarcode): InputCapture {
        val order = requireNotNull(store.db.orders().get(input.orderId))
        check(order.state == "SCANNING") { "订单已冻结，不接收新输入" }
        val device = store.device()
        val barcode = input.raw.trim(' ', '\r', '\n', '\t').uppercase(java.util.Locale.ROOT)
        val context = CaptureContext(order.id, device.id, order.nextOrderScanSeq,
            device.nextCaptureSeq, input.raw, barcode, input.source, input.receivedAt,
            input.receivedElapsedNs, input.bootId, order.snapshotId,
            store.db.inventory().state(barcode)?.dispatchCycle, order.recoveryEpoch, order.authorizationId,
            confirmationPolicy = if (input.source in setOf("BROADCAST", "TEST_INJECTION")) "AUTO_AFTER_VALIDATION" else "MANUAL")
        val capture = InputCapture(input.id, context)
        store.db.captures().insert(capture)
        check(store.db.orders().advanceCaptureSequence(order.id, order.nextOrderScanSeq,
            Math.addExact(order.nextOrderScanSeq, 1L)) == 1) { "Order sequence changed; stop capture" }
        store.audit(order.id, input.id, AuditDetail("RECEIVED"), device.copy(nextCaptureSeq = device.nextCaptureSeq + 1))
        return capture
    }

    fun validate(id: String): InputCapture {
        lateinit var result: InputCapture
        store.db.runInTransaction {
            val capture = requireNotNull(store.db.captures().get(id))
            if (capture.state != "RECEIVED") { result = capture; return@runInTransaction }
            val error = Validation.reject(store.validation(capture))
            result = capture.copy(state = if (error == null) "WAITING_CONFIRM" else "INVALID",
                error = error.orEmpty(), inputCommittedNs = store.clock.elapsedNs())
            store.db.captures().updateProgress(result)
            if (error != null) store.audit(capture.context.orderId, id, AuditDetail("INVALID", error))
        }
        return result
    }

    fun prepareAutomaticSave(id: String): InputCapture {
        lateinit var result: InputCapture
        store.db.runInTransaction {
            val c = requireNotNull(store.db.captures().get(id))
            require(c.context.confirmationPolicy == "AUTO_AFTER_VALIDATION")
            if (c.state != "RECEIVED") { result = c; return@runInTransaction }
            val error = Validation.reject(store.validation(c))
            val now = store.clock.elapsedNs()
            result = c.copy(state = if (error == null) "CONFIRM_REQUESTED" else "INVALID",
                error = error.orEmpty(), inputCommittedNs = now, confirmedAtNs = if (error == null) now else null)
            store.db.captures().updateProgress(result)
            val detail = if (error == null) AuditDetail("CONFIRM_REQUESTED",
                "${c.context.confirmationPolicy}:${c.context.inputSource}") else AuditDetail("INVALID", error)
            store.audit(c.context.orderId, id, detail)
        }
        return result
    }
}
