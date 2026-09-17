package com.acrylic.m71demo.domain

import com.acrylic.m71demo.data.*

data class ValidationContext(
    val capture: InputCapture,
    val order: DemoOrder,
    val line: OrderLine?,
    val bale: InventoryBale?,
    val cloud: CloudBaleState?,
    val guard: BarcodeGuard?,
    val plannedTotal: Long,
    val authError: String?,
)

object Validation {
    fun reject(v: ValidationContext): String? = contextError(v) ?: stockError(v) ?: planError(v)

    private fun contextError(v: ValidationContext): String? {
        if (v.order.state != "SCANNING") return "ORDER_NOT_SCANNING"
        if (v.authError != null) return v.authError
        if (v.capture.context.sourceRecoveryEpoch != v.order.recoveryEpoch) return "RECOVERY_EPOCH_MISMATCH"
        if (!v.capture.context.barcode.matches(Regex("[0-9A-Z]{11}"))) return "BARCODE_FORMAT"
        return null
    }

    private fun stockError(v: ValidationContext): String? {
        if (v.bale == null) return "BARCODE_NOT_IN_SNAPSHOT"
        if (v.line == null) return "PRODUCT_MISMATCH"
        if (v.cloud?.dispatchCycle != v.capture.context.expectedDispatchCycle) return "DISPATCH_CYCLE_CONFLICT"
        if (v.cloud?.state != "AVAILABLE" || v.guard != null) return "BARCODE_ALREADY_OCCUPIED"
        return null
    }

    private fun planError(v: ValidationContext): String? {
        if (v.line!!.actualCount >= v.line.plannedCount) return "PLAN_EXCEEDED"
        if (v.order.actualCount.toLong() >= v.plannedTotal) return "ORDER_TOTAL_EXCEEDED"
        return null
    }
}
