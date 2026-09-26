package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.network.*
import com.acrylic.outbound.pda.sync.SyncRun
import com.acrylic.outbound.pda.temporary.TemporaryWorkStore
import com.acrylic.outbound.pda.update.UpdateGate
import org.json.JSONObject
import java.util.concurrent.Callable
import java.io.IOException

/** Caller owns one instance per application. Network calls are always outside the Room transaction. */
class LocalDraftRecoveryRepository(private val stores: () -> TemporaryWorkStore) {
    data class Progress(val state: String, val verifiedRequests: Long, val totalRequests: Long)
    @Synchronized fun prepare(id: String): LocalDraftTransfer = UpdateGate.business {
        val store = stores()
        try {
            val plan = LocalDraftTransferRepository(store).prepare(id)
            store.db.runInTransaction {
                val result = inspect(attempt(store, id, SyncRun()))
                val dao = store.db.localDraftQueue(); val prior = dao.get(id)
                val row = LocalDraftQueue(id, result.state, result.verifiedRequests, plan.requestCount,
                    autoRetry = result.state != "ORIGINALS_STORED")
                if (prior == null) dao.insert(row) else dao.update(row)
            }
            plan
        } catch (error: Exception) { failed(store, id, error); throw error }
    }
    /** Local verification only; rendering a page never sends or enables a request. */
    @Synchronized fun checked(id: String): Progress = UpdateGate.business {
        val store = stores()
        try { store.db.runInTransaction(Callable { inspect(attempt(store, id, SyncRun())) }) }
        catch (error: Exception) { failed(store, id, error); throw error }
    }
    @Synchronized fun resume(id: String, control: SyncRun = SyncRun()): Progress = UpdateGate.business {
        val store = stores(); checkNotNull(store.db.localDraftQueue().get(id)) { "请先明确选择补传原件" }
        try {
            val a = attempt(store, id, control)
            val inspected = store.db.runInTransaction(Callable { inspect(a) })
            val queued = checkNotNull(store.db.localDraftQueue().get(id))
            check(queued.autoRetry || inspected.state == "ORIGINALS_STORED") { "补传已暂停，请在原身份下明确选择补传原件" }
            send(a).also { progress(store, id, it) }
        } catch (error: Exception) { failed(store, id, error); throw error }
    }
    private fun failed(store: TemporaryWorkStore, id: String, error: Exception) {
        store.db.runInTransaction {
            val prior = store.db.localDraftQueue().get(id) ?: return@runInTransaction
            val retry = error is IOException || error is java.util.concurrent.CancellationException ||
                error is ApiFailure && (error.httpStatus == 429 || (error.httpStatus ?: 0) in 500..599)
            store.db.localDraftQueue().update(prior.copy(state = if (retry) "RESULT_UNKNOWN" else "ACTION_REQUIRED",
                autoRetry = retry && prior.autoRetry, error = error.message.orEmpty().take(500)))
        }
    }
    private fun attempt(store: TemporaryWorkStore, id: String, control: SyncRun): Attempt {
        val plan = LocalDraftTransferRepository(store).read(id)
        val stop = checkNotNull(store.db.localDraftStops().get(id))
        val expected = JSONObject(checkNotNull(store.connection.current()).toString())
        val keys = listOfNotNull(JSONObject(store.source(id).request.signingKeyJson), expected.optJSONObject("signingKey"))
        return Attempt(store, plan, stop, expected, control, LocalDraftReceiptStore(store.db, keys),
            LocalDraftTransport(store.db, store.connection, expected, stop), counts(store, stop))
    }
    private fun inspect(a: Attempt): Progress {
        var verified = 0L
        for (position in 0 until a.plan.requestCount) {
            val request = checkNotNull(a.store.db.localDraftTransfers().request(a.stop.clientOrderId, position))
            check(a, request)
            if (a.receipts.verified(LocalDraftReceiptVerifier.Expected(a.stop, request, a.counts))) verified++
        }
        val complete = verified == a.plan.requestCount
        val queued = a.store.db.localDraftQueue().get(a.stop.clientOrderId)
        if (queued?.state == "ORIGINALS_STORED") kotlin.check(complete) { "已保存标签缺少原回执，请保留原资料" }
        return Progress(if (complete) "ORIGINALS_STORED" else "READY", verified, a.plan.requestCount)
    }
    private fun progress(store: TemporaryWorkStore, id: String, result: Progress) {
        store.db.runInTransaction {
            val prior = checkNotNull(store.db.localDraftQueue().get(id))
            store.db.localDraftQueue().update(prior.copy(state = result.state, verifiedRequests = result.verifiedRequests,
                totalRequests = result.totalRequests, autoRetry = prior.autoRetry && result.state != "ORIGINALS_STORED", error = ""))
        }
    }
    private fun send(a: Attempt): Progress {
        var verified = 0L; var sent = 0
        while (verified < a.plan.requestCount) {
            val request = checkNotNull(a.store.db.localDraftTransfers().request(a.stop.clientOrderId, verified))
            val facts = LocalDraftReceiptVerifier.Expected(a.stop, request, a.counts)
            if (a.store.db.runInTransaction(Callable { check(a, request); a.receipts.verified(facts) })) { verified++; continue }
            if (sent >= a.control.batches) return Progress("RESULT_UNKNOWN", verified, a.plan.requestCount)
            a.store.db.runInTransaction { check(a, request); requireEnabled(a); a.receipts.pending(request) }
            check(a, request); requireEnabled(a)
            val bytes = a.transport.receive(request)
            a.store.db.runInTransaction { check(a, request); a.receipts.received(request, bytes) }
            a.store.db.runInTransaction { check(a, request); kotlin.check(a.receipts.verified(facts)) }
            verified++; sent++
        }
        return Progress("ORIGINALS_STORED", verified, a.plan.requestCount)
    }
    private fun requireEnabled(a: Attempt) {
        kotlin.check(a.store.db.localDraftQueue().get(a.stop.clientOrderId)?.autoRetry == true) {
            "补传已暂停，请在原身份下明确选择补传原件"
        }
    }
    private fun check(a: Attempt, request: LocalDraftRequest) {
        a.control.check(); SessionIdentity.requireSame(a.expected, a.store.connection.current())
        a.store.check(a.stop.clientOrderId, false)
        kotlin.check(PdaCapabilities.recovery(checkNotNull(a.store.connection.current()))) { "当前会话没有证据恢复权限" }
        kotlin.check(a.store.db.localDraftStops().get(a.stop.clientOrderId) == a.stop && a.store.db.localDraftTransfers().get(a.stop.clientOrderId) == a.plan)
        kotlin.check(a.store.db.localDraftTransfers().request(request.clientOrderId, request.position) == request) { "原请求变化，停止发送" }
    }
    private fun counts(store: TemporaryWorkStore, stop: LocalDraftStop): LocalDraftReceiptVerifier.Counts {
        var own = 0L; var other = 0L; var archives = 0L; var after = 0L
        while (true) {
            val rows = store.db.localDraftStops().page(stop.clientOrderId, after); if (rows.isEmpty()) break
            for (row in rows) {
                if (row.kind == "ARCHIVE") archives++
                if (row.kind == "CAPTURE") {
                    if (JSONObject(row.rawBytes.toString(Charsets.UTF_8)).optString("clientOrderId") == stop.clientOrderId) own++ else other++
                }
            }
            after = rows.last().ordinal
        }
        return LocalDraftReceiptVerifier.Counts(own, other, archives)
    }
    private data class Attempt(val store: TemporaryWorkStore, val plan: LocalDraftTransfer, val stop: LocalDraftStop,
        val expected: JSONObject, val control: SyncRun, val receipts: LocalDraftReceiptStore, val transport: LocalDraftTransport,
        val counts: LocalDraftReceiptVerifier.Counts)
}
