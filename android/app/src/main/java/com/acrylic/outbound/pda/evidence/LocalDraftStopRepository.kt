package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.temporary.TemporaryWorkStore
import com.acrylic.outbound.pda.update.UpdateGate
import java.util.UUID
import java.util.concurrent.Callable

/** Preserves unsubmitted work without manufacturing a frozen payload, S, STOP_PROOF or server result. */
class LocalDraftStopRepository(private val store: TemporaryWorkStore) {
    fun stop(id: String): LocalDraftStop = UpdateGate.business {
        store.load(id)
        store.db.runInTransaction(Callable {
            val draft = store.check(id, false)
            store.db.localDraftStops().get(id)?.let {
                LocalDraftStopIntegrity(store).verify(it, draft)
                LocalDraftStopSignatures(store).require(it); return@Callable it
            }
            check(draft.state in setOf("DRAFT", "SCANNING")) { "原停止证据缺失或作业已经冻结，请保留原资料" }
            check(store.db.temporarySubmissions().forOrder(id) == null && store.db.localE2aStops().get(id) == null)
            check(store.db.temporaryScans().events(id).none { it.type in setOf("FROZEN", "STOP", "LOCAL_EVIDENCE_STOP") })
            val evidenceId = UUID.randomUUID().toString()
            val sequence = store.event(draft, null, "LOCAL_EVIDENCE_STOP", evidenceId)
            val event = store.db.temporaryScans().events(id).single { it.localSeq == sequence }
            val boundary = E2aDeviceCoverage(store.db).verify(draft.creatorDeviceId)
            check(sequence == boundary.auditThroughSeq)
            val seed = LocalDraftStop(id, evidenceId, LocalDraftStopSource.metadata(store, draft, evidenceId, event.id, boundary), "", 0, 0)
            store.db.localDraftStops().insert(seed)
            LocalDraftStopSnapshot(store.db, seed).save()
            store.db.temporaryDrafts().update(draft.copy(state = "EVIDENCE_STOPPED"))
            checkNotNull(store.db.localDraftStops().get(id)).also {
                LocalDraftStopIntegrity(store).verify(it, store.check(id, false))
                LocalDraftStopSignatures(store).create(it)
                store.check(id, false)
            }
        })
    }

    fun read(id: String): LocalDraftStop {
        store.load(id)
        return store.db.runInTransaction(Callable {
            checkNotNull(store.db.localDraftStops().get(id)) { "原停止证据不存在" }.also {
                LocalDraftStopIntegrity(store).verify(it, store.check(id, false))
                LocalDraftStopSignatures(store).require(it)
            }
        })
    }
}
