package com.acrylic.outbound.pda.temporary

class TemporaryProcessor(private val store: TemporaryWorkStore, private val feedback: (String) -> Unit) {
    val reception = TemporaryReception(store)
    val saving = TemporarySaving(store)
    val corrections = TemporaryCorrections(store)
    fun drain(id: String, active: () -> Boolean = { true }) {
        while (active() && store.db.temporaryScans().failure(id) == null) {
            val pending = store.db.temporaryScans().pending(id) ?: return
            val row = if (pending.state == "RECEIVED") reception.validate(pending.id) else pending
            when (row.state) {
                "INVALID" -> { feedback("ERROR"); return }
                "WAITING_CONFIRM" -> return
                "CONFIRM_REQUESTED" -> {
                    if (!active()) return
                    when (saving.save(row.id)) {
                        "SAVED" -> feedback("SAVED")
                        "BUSINESS_REJECTED" -> { feedback("ERROR"); return }
                    }
                }
                else -> error("临时输入状态异常，保留原记录并停止作业")
            }
        }
    }
}
