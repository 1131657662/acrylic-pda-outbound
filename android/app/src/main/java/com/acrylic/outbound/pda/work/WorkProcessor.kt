package com.acrylic.outbound.pda.work

class WorkProcessor(private val store: WorkStore, private val feedback: (String, String) -> Unit) {
    val reception = WorkReception(store)
    val saving = WorkSaving(store)
    val corrections = WorkCorrections(store)

    fun drain(assignment: String, active: () -> Boolean = { true }) {
        while (active() && store.db.captures().failure(assignment) == null) {
            val pending = store.db.captures().pending(assignment) ?: return
            val c = if (pending.state == "RECEIVED") reception.validate(pending.id) else pending
            when (c.state) {
                "INVALID" -> { feedback("ERROR", c.id); return }
                "WAITING_CONFIRM" -> return
                "CONFIRM_REQUESTED" -> {
                    if (!active()) return
                    when (saving.save(c.id)) {
                        "SAVED" -> feedback("SAVED", c.id)
                        "BUSINESS_REJECTED" -> { feedback("ERROR", c.id); return }
                    }
                }
                else -> error("输入状态异常，保留记录并停止作业")
            }
        }
    }
}
