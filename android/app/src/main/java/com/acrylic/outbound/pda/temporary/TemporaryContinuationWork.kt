package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.update.UpdateGate
import java.util.concurrent.Callable

/** Explicit isolated consumer. A signed closed production capability never becomes a production grant. */
class TemporaryContinuationWork(private val store: TemporaryWorkStore) {
    fun start(id: String): TemporaryDraft = UpdateGate.business {
        check(com.acrylic.outbound.pda.BuildConfig.DEBUG && store.continuationDevelopment) { "续作开发能力未启用，原记录保留" }
        store.load(id)
        store.db.runInTransaction(Callable {
            val draft = store.check(id); store.requireNoOtherInput(id)
            check(draft.workRound > 1 && draft.state in setOf("CONTINUATION_BLOCKED", "SCANNING"))
            TemporaryContinuationBaseline(store).read(draft, true)
            if (draft.state == "SCANNING") return@Callable draft
            draft.copy(state = "SCANNING").also {
                store.db.temporaryDrafts().update(it)
                store.event(it, null, "TEMP_CONTINUATION_WORK_STARTED")
                store.check(id)
            }
        })
    }
}
