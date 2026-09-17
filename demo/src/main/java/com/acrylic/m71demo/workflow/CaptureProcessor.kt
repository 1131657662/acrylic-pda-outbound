package com.acrylic.m71demo.workflow

import com.acrylic.m71demo.data.InputCapture
import com.acrylic.m71demo.device.DemoHardware
import javax.inject.Inject

/** Runs only on ScanEngine's serial worker. The persisted policy controls restart behavior. */
class CaptureProcessor @Inject constructor(private val store: DemoStore,
    private val services: WorkflowServices, private val hardware: DemoHardware) {
    var message = "扫码自动保存；手输校验后回车"
        private set

    fun receive(input: IncomingBarcode) {
        hardware.fault.hit("BEFORE_CAPTURE", input.id)
        val started = store.clock.elapsedNs()
        services.capture.receive(input)
        hardware.telemetry.event("CAPTURE_DURABLE", mapOf("captureId" to input.id,
            "orderId" to input.orderId, "receivedElapsedNs" to input.receivedElapsedNs,
            "dbServiceNs" to store.clock.elapsedNs() - started))
        hardware.fault.hit("AFTER_CAPTURE", input.id)
        drain(input.orderId)
    }

    fun drain(orderId: String) {
        while (store.db.captures().failure(orderId) == null) {
            val pending = store.db.captures().pending(orderId) ?: return
            val c = if (pending.state == "RECEIVED") validate(pending) else pending
            when (c.state) {
                "INVALID" -> return
                "WAITING_CONFIRM" -> {
                    if (c.context.confirmationPolicy != "AUTO_AFTER_VALIDATION") return
                    requestAndSave(c.id, store.clock.elapsedNs())
                }
                "CONFIRM_REQUESTED", "STORAGE_ERROR" -> saveSafely(c.id)
                else -> error("未知输入状态，暂停核对：${c.state}")
            }
        }
    }

    fun confirm(id: String, pressedAtNs: Long) {
        val c = requireNotNull(store.db.captures().get(id))
        check(store.db.captures().failure(c.context.orderId) == null) { "先确认处理错误" }
        if (c.context.confirmationPolicy != "MANUAL" || store.db.captures().pending(c.context.orderId)?.id != id) {
            hardware.telemetry.event("CONFIRM_IGNORED", mapOf("captureId" to id))
            return
        }
        requestAndSave(id, pressedAtNs)
        drain(c.context.orderId)
    }

    private fun validate(pending: InputCapture): InputCapture {
        val id = pending.id
        val started = store.clock.elapsedNs()
        lateinit var c: InputCapture
        com.acrylic.m71demo.device.TransactionTiming.run(store.db, "VALIDATE_INTENT", id) {
            c = if (pending.context.confirmationPolicy == "AUTO_AFTER_VALIDATION")
                services.capture.prepareAutomaticSave(id) else services.capture.validate(id)
        }
        hardware.telemetry.event("VALIDATED", mapOf("captureId" to id, "dbServiceNs" to store.clock.elapsedNs() - started,
            "includesAutomaticConfirmation" to (c.state == "CONFIRM_REQUESTED")))
        if (c.state == "CONFIRM_REQUESTED") {
            hardware.telemetry.event("CONFIRM_DURABLE", mapOf("captureId" to id, "requestedAtNs" to c.confirmedAtNs,
                "confirmationPolicy" to c.context.confirmationPolicy, "dbServiceNs" to 0,
                "serviceIncludedIn" to "VALIDATED"))
            try { hardware.fault.hit("AFTER_CONFIRM", id) }
            catch (e: Exception) {
                runCatching { services.confirmation.markStorageError(id) }
                throw e
            }
        }
        if (c.state == "INVALID") {
            message = "本包未保存；处理错误后恢复队列"
            hardware.sound.play("ERROR", id)
        }
        return c
    }

    private fun requestAndSave(id: String, requestedAtNs: Long) {
        val started = store.clock.elapsedNs()
        if (!services.confirmation.request(id, requestedAtNs)) {
            error("保存意图未登记，暂停核对原输入：$id")
        }
        val c = requireNotNull(store.db.captures().get(id))
        hardware.telemetry.event("CONFIRM_DURABLE", mapOf("captureId" to id,
            "requestedAtNs" to requestedAtNs, "confirmationPolicy" to c.context.confirmationPolicy,
            "dbServiceNs" to store.clock.elapsedNs() - started))
        try {
            hardware.fault.hit("AFTER_CONFIRM", id)
            saveSafely(id)
        } catch (e: Exception) {
            runCatching { services.confirmation.markStorageError(id) }
            throw e
        }
    }

    private fun saveSafely(id: String) {
        try { save(id) }
        catch (e: Exception) {
            runCatching { services.confirmation.markStorageError(id) }
            throw e
        }
    }

    private fun save(id: String) {
        val c = requireNotNull(store.db.captures().get(id))
        hardware.fault.hit("BEFORE_SAVE", id)
        val started = store.clock.elapsedNs()
        val result = services.confirmation.save(id)
        check(result.status != "IGNORED") { "原保存意图状态异常，暂停核对：$id" }
        val committed = store.clock.elapsedNs()
        hardware.telemetry.event("SAVE_RESULT", mapOf("captureId" to id, "status" to result.status,
            "committedNs" to committed, "pressedAtNs" to result.pressedAtNs,
            "receivedElapsedNs" to c.context.receivedElapsedNs, "captureBoot" to c.context.bootSessionId,
            "confirmationPolicy" to c.context.confirmationPolicy, "bootSessionId" to store.clock.bootId(),
            "error" to result.error, "dbServiceNs" to committed - started))
        hardware.fault.hit("AFTER_SAVE", id)
        if (result.status == "SAVED") hardware.sound.play("SAVED", id)
        if (result.status == "BUSINESS_REJECTED") hardware.sound.play("ERROR", id)
        message = if (result.status in setOf("SAVED", "ALREADY_SAVED")) "已保存（本地），等待上传" else "本包未保存：${result.error}"
        hardware.fault.hit("AFTER_FEEDBACK", id)
    }
}
