package com.acrylic.outbound.pda.independent

internal class IndependentScanProcessor(val store: IndependentScanStore, private val feedback: (String) -> Unit) {
    val reception = IndependentScanReception(store)
    val saving = IndependentScanSaving(store)
    val corrections = IndependentScanCorrections(store)
    fun drain(id: String, active: () -> Boolean) {
        while (active() && store.dao.failure(id) == null) {
            val pending = store.dao.pending(id) ?: return
            val row = if (pending.processing.state == "RECEIVED") reception.validate(pending.capture.id) else pending
            when (row.processing.state) {
                "INVALID" -> { feedback("ERROR"); return }
                "WAITING_CONFIRM" -> return
                "CONFIRM_REQUESTED" -> {
                    if (!active()) return
                    val result = saving.save(row.capture.id)
                    if (result == "SAVED") feedback("SAVED")
                    if (result == "BUSINESS_REJECTED") { feedback("ERROR"); return }
                }
                else -> error("原独立输入状态异常，请停扫保留资料")
            }
        }
    }
}
