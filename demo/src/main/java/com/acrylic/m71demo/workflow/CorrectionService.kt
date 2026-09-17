package com.acrylic.m71demo.workflow

import com.acrylic.m71demo.data.InputCapture
import javax.inject.Inject

class CorrectionService @Inject constructor(private val store: DemoStore) {
    fun acknowledge(id: String) = store.db.runInTransaction {
        val c = requireNotNull(store.db.captures().get(id))
        check(c.state in setOf("INVALID", "BUSINESS_REJECTED"))
        store.db.captures().updateProgress(c.copy(failureAcknowledged = true))
        store.audit(c.context.orderId, id, AuditDetail("FAILURE_ACK", c.error))
    }

    fun discard(id: String) = store.db.runInTransaction {
        val c = requireNotNull(store.db.captures().get(id))
        check(c.state == "WAITING_CONFIRM" && c.confirmedAtNs == null && c.context.confirmationPolicy == "MANUAL") {
            "已确认或自动保存意图不能放弃，先恢复处理" }
        store.db.captures().updateProgress(c.copy(state = "DISCARDED"))
        store.audit(c.context.orderId, id, AuditDetail("DISCARDED", "操作员放弃未确认输入"))
    }

    fun voidLast(orderId: String, reason: String) = store.db.runInTransaction {
        require(reason.isNotBlank() && reason.length <= 500)
        val order = requireNotNull(store.db.orders().get(orderId))
        check(order.state == "SCANNING") { "冻结后不可撤销" }
        val scan = requireNotNull(store.db.scans().lastActive(orderId)) { "没有可撤销明细" }
        val line = requireNotNull(store.db.orders().line(orderId, scan.deliveryCode))
        store.db.scans().update(scan.copy(status = "VOID"))
        store.db.scans().release(scan.id)
        store.db.orders().update(order.copy(actualCount = order.actualCount - 1, weightGrams = order.weightGrams - scan.weightGrams))
        store.db.orders().updateLine(line.copy(actualCount = line.actualCount - 1, weightGrams = line.weightGrams - scan.weightGrams))
        store.audit(orderId, scan.id, AuditDetail("VOID", reason))
    }

    fun note(orderId: String, text: String) = store.db.runInTransaction {
        require(text.isNotBlank() && text.length <= 500)
        store.audit(orderId, null, AuditDetail("EXCEPTION_NOTE", text))
    }
}
