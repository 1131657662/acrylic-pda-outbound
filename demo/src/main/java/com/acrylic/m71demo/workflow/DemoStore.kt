package com.acrylic.m71demo.workflow

import com.acrylic.m71demo.data.*
import com.acrylic.m71demo.domain.*
import javax.inject.Inject

class DemoStore @Inject constructor(val db: DemoDatabase, val clock: AppClock,
    val authorization: AuthorizationService) {
    fun device(): DeviceState = requireNotNull(db.audit().device())

    fun audit(orderId: String, captureId: String?, detail: AuditDetail, device: DeviceState = device()): Long {
        db.audit().insert(AuditRecord(java.util.UUID.randomUUID().toString(), device.id,
            device.nextAuditSeq, orderId, captureId, detail.type, detail.reason,
            clock.wallMs(), clock.elapsedNs(), clock.bootId()))
        db.audit().updateDevice(device.copy(nextAuditSeq = device.nextAuditSeq + 1))
        return device.nextAuditSeq
    }

    fun validation(capture: InputCapture): ValidationContext {
        val c = capture.context
        val rows = requireNotNull(db.validation().context(c.orderId, c.snapshotId, c.barcode, c.authorizationId))
        val auth = rows.auth
        val error = if (auth?.deviceId != c.originDeviceId) "AUTH_DEVICE_MISMATCH" else authorization.error(auth)
        return ValidationContext(capture, rows.order, rows.line, rows.bale, rows.cloud, rows.guard,
            rows.plannedTotal, error)
    }
}

data class AuditDetail(val type: String, val reason: String = "")
