package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.network.*
import com.acrylic.outbound.pda.sync.SyncRun
import com.acrylic.outbound.pda.temporary.TemporaryWorkStore
import com.acrylic.outbound.pda.update.UpdateGate
import org.json.JSONObject
import java.io.IOException

/** Application singleton serializes UI and worker writes; no network request holds a Room transaction. */
class LocalE2aRecoveryRepository(private val stores: () -> TemporaryWorkStore,
    private val syncAudit: (SyncRun) -> Unit) {
    @Synchronized fun queue(id: String): LocalE2aQueue = UpdateGate.business {
        val store = stores()
        LocalE2aTransferRepository(store).record(id)
        store.db.runInTransaction {
            store.check(id, false)
            check(PdaCapabilities.recovery(checkNotNull(store.connection.current()))) { "当前会话没有证据恢复权限" }
            val dao = store.db.localE2aReceipts()
            val prior = dao.queue(id)
            if (prior == null) dao.insertQueue(LocalE2aQueue(id))
            else if (prior.state != "PARTIAL") dao.updateQueue(prior.copy(state = "READY", autoRetry = true, error = ""))
        }
        checkNotNull(store.db.localE2aReceipts().queue(id))
    }

    @Synchronized fun resume(id: String, control: SyncRun = SyncRun()): LocalE2aQueue = UpdateGate.business {
        val store = stores()
        val dao = store.db.localE2aReceipts()
        val queued = checkNotNull(dao.queue(id)) { "请先在原临时单选择补传证据" }
        try {
            val plan = LocalE2aTransferRepository(store).record(id)
            val stop = checkNotNull(store.db.localE2aStops().get(id))
            val expected = JSONObject(checkNotNull(store.connection.current()).toString())
            val receipts = E2aReceiptStore(store.db, trustedKeys(store, id, expected))
            val transport = E2aTransport(store.db, store.connection, expected)
            val attempt = Attempt(store, stop, plan, expected, control, receipts, transport)
            send(attempt)
        } catch (error: Exception) {
            val retry = error is IOException || error is java.util.concurrent.CancellationException ||
                error is ApiFailure && (error.httpStatus == 429 || (error.httpStatus ?: 0) in 500..599)
            val current = checkNotNull(dao.queue(id))
            dao.updateQueue(current.copy(state = if (retry) "RESULT_UNKNOWN" else "ACTION_REQUIRED",
                autoRetry = retry && queued.autoRetry, error = error.message.orEmpty().take(500)))
            throw error
        }
        checkNotNull(dao.queue(id))
    }

    private fun send(attempt: Attempt) {
        var sent = 0
        for (request in E2aRequestSequence(attempt.store.db, attempt.plan).all()) {
            val facts = E2aReceiptVerifier.Expected(attempt.stop, attempt.plan, request)
            checkCurrent(attempt, request)
            if (attempt.store.db.runInTransaction(java.util.concurrent.Callable {
                checkCurrent(attempt, request); attempt.receipts.verified(facts)
            })) continue
            if (sent >= attempt.control.batches) return
            if (request.operation == "VERIFY" && !auditReady(attempt)) return
            attempt.store.db.runInTransaction {
                checkCurrent(attempt, request)
                attempt.receipts.pending(request)
                progress(attempt, "RESULT_UNKNOWN", true)
            }
            receive(attempt, facts)
            sent++
        }
        attempt.store.db.runInTransaction { checkCurrent(attempt, null); progress(attempt, "PARTIAL", false) }
    }

    private fun auditReady(attempt: Attempt): Boolean {
        fun acknowledged() = (attempt.store.db.audit().checkpoint(attempt.stop.sourceRecoveryEpoch.toString(),
            attempt.stop.freezeDeviceId)?.acknowledgedThrough ?: 0) >= attempt.stop.auditThroughSeq
        if (acknowledged()) return true
        check(PdaCapabilities.audit(checkNotNull(attempt.store.connection.current()))) { "当前会话没有操作记录补传权限，请保留原证据" }
        syncAudit(attempt.control)
        checkCurrent(attempt, null)
        if (acknowledged()) return true
        attempt.store.db.runInTransaction { checkCurrent(attempt, null); progress(attempt, "WAITING_AUDIT", true) }
        return false
    }

    private fun receive(attempt: Attempt, facts: E2aReceiptVerifier.Expected) {
        val bytes = attempt.transport.receive(facts)
        attempt.store.db.runInTransaction {
            checkCurrent(attempt, facts.request)
            attempt.receipts.received(facts.request, bytes)
        }
        attempt.store.db.runInTransaction {
            checkCurrent(attempt, facts.request)
            check(attempt.receipts.verified(facts))
            progress(attempt, "READY", true)
        }
    }

    private fun checkCurrent(attempt: Attempt, request: LocalE2aRequest?) {
        attempt.control.check()
        val store = attempt.store
        SessionIdentity.requireSame(attempt.expected, store.connection.current())
        store.check(attempt.stop.clientOrderId, false)
        check(PdaCapabilities.recovery(checkNotNull(store.connection.current()))) { "当前会话没有证据恢复权限" }
        check(store.db.localE2aStops().get(attempt.stop.clientOrderId) == attempt.stop &&
            store.db.localE2aTransfers().get(attempt.stop.clientOrderId) == attempt.plan)
        if (request != null) check(store.db.localE2aTransfers().request(request.clientOrderId,
            request.operation, request.ordinal) == request) { "原请求已变化，停止发送" }
    }

    private fun progress(attempt: Attempt, state: String, retry: Boolean) {
        val dao = attempt.store.db.localE2aReceipts()
        val current = checkNotNull(dao.queue(attempt.stop.clientOrderId))
        dao.updateQueue(current.copy(state = state, autoRetry = retry, error = ""))
    }

    private fun trustedKeys(store: TemporaryWorkStore, id: String, expected: JSONObject): List<JSONObject> {
        val request = store.source(id).request
        return listOfNotNull(JSONObject(request.signingKeyJson), expected.optJSONObject("signingKey"))
    }

    private data class Attempt(val store: TemporaryWorkStore, val stop: LocalE2aStop, val plan: LocalE2aTransfer,
        val expected: JSONObject, val control: SyncRun, val receipts: E2aReceiptStore, val transport: E2aTransport)
}
