package com.acrylic.outbound.pda.independent

import com.acrylic.outbound.pda.rescue.*
import com.acrylic.outbound.pda.update.UpdateGate
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Callable

/** A single Room transaction seals originals without releasing their effective barcode guards. */
internal class IndependentTaskClosing(private val store: IndependentScanStore, private val signer: () -> RescueSigner) {
    fun close(id: String, reason: String, confirmed: Boolean): JSONObject = UpdateGate.business {
        check(confirmed) { "请确认结束后不能新增或撤销；仍待主管对账" }
        store.db.runInTransaction(Callable {
            val task = store.owner(id)
            val snapshots = RescueIndependentSnapshot(store.db, RescueRows(store.db.openHelper.readableDatabase))
            if (store.db.independentTasks().state(id)?.state == "SEALED") {
                return@Callable checkNotNull(IndependentClosure.verify(snapshots.readCaptured(id, store.environment.actor())))
            }
            store.check(id); store.requireNoOtherInput(id); store.verifyTotals(id)
            val original = snapshots.readCaptured(id, store.environment.actor())
            val time = store.environment.sample(); val envelope = IndependentClosure.seal(original, time, reason, signer())
            store.authorize(task)
            val state = checkNotNull(store.db.independentTasks().state(id))
            store.db.independentTasks().insertEvent(IndependentTaskEvent(UUID.randomUUID().toString(), id, state.nextEventSeq,
                IndependentClosure.EVENT, envelope.toString(), time.wall, time.elapsed, time.boot))
            store.db.independentTasks().updateState(state.copy(state = "SEALED", nextEventSeq = Math.addExact(state.nextEventSeq, 1)))
            checkNotNull(IndependentClosure.verify(snapshots.readCaptured(id, store.environment.actor())))
        })
    }
}
