package com.acrylic.outbound.pda.sync

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.work.CaptureContext

/** Cloud availability is a restriction only; it cannot release a local guard or refresh captured cycles. */
class BarcodeWorkGuard(private val db: PdaDatabase) {
    fun reason(capture: CaptureContext): String? {
        val row = db.barcodes().active(capture.sourceRecoveryEpoch, capture.warehouseId, capture.barcode) ?: return null
        if (row.dispatchCycle != capture.expectedDispatchCycle) return "云端出库周期已变化，请保留原记录并联系主管核对"
        return when (row.status) {
            "AVAILABLE" -> null
            "DISPATCHED" -> "该包已在云端确认出库，不能再次装车"
            "QUARANTINED" -> "该包已隔离，请联系主管处理"
            "RESERVED" -> "该包已有云端占用，请核对所属订单"
            else -> "该包当前不可用，请联网核对库存状态"
        }
    }
}
