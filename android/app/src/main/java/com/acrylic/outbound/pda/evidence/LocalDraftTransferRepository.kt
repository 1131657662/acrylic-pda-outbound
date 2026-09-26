package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.network.PdaCapabilities
import com.acrylic.outbound.pda.temporary.TemporaryWorkStore
import com.acrylic.outbound.pda.update.UpdateGate
import java.util.UUID
import java.util.concurrent.Callable

class LocalDraftTransferRepository(private val store: TemporaryWorkStore) {
    /** Explicit preparation never re-signs the stop and never performs network I/O. */
    fun prepare(id: String): LocalDraftTransfer = UpdateGate.business {
        val stop = LocalDraftStopRepository(store).read(id)
        store.db.runInTransaction(Callable {
            access(id)
            check(store.db.localDraftStops().get(id) == stop)
            LocalDraftStopSignatures(store).require(stop)
            val dao = store.db.localDraftTransfers()
            if (dao.get(id) == null) {
                check(dao.count(id) == 0L) { "原传输计划不完整，不可补造原键" }
                create(stop)
            }
            verify(stop)
        })
    }
    fun read(id: String): LocalDraftTransfer {
        val stop = LocalDraftStopRepository(store).read(id)
        return store.db.runInTransaction(Callable {
            access(id); check(store.db.localDraftStops().get(id) == stop)
            LocalDraftStopSignatures(store).require(stop); verify(stop)
        })
    }
    private fun access(id: String) {
        store.check(id, false)
        check(PdaCapabilities.recovery(checkNotNull(store.connection.current()))) { "当前会话没有证据恢复权限" }
    }
    private fun create(stop: LocalDraftStop) {
        val digest = LocalDraftRequestDigest(stop)
        for (body in LocalDraftRequests(store.db, stop).all()) {
            val row = LocalDraftRequest(stop.clientOrderId, body.position, body.operation, UUID.randomUUID().toString(),
                body.canonical, E2aWire.sha(body.canonical.toByteArray(Charsets.UTF_8)))
            store.db.localDraftTransfers().insertRequest(row); digest.append(row)
        }
        store.db.localDraftTransfers().insert(LocalDraftTransfer(stop.clientOrderId, stop.evidenceId, digest.count, digest.finish()))
    }
    private fun verify(stop: LocalDraftStop): LocalDraftTransfer {
        val dao = store.db.localDraftTransfers()
        val plan = checkNotNull(dao.get(stop.clientOrderId)) { "原传输计划不存在，请保留原件" }
        check(plan.evidenceId == stop.evidenceId && plan.requestCount == stop.recordCount + 2 && dao.count(stop.clientOrderId) == plan.requestCount)
        val digest = LocalDraftRequestDigest(stop); val keys = hashSetOf<String>()
        for (body in LocalDraftRequests(store.db, stop).all()) {
            val row = checkNotNull(dao.request(stop.clientOrderId, body.position)) { "原请求缺失，不可补键" }
            check(row.operation == body.operation && row.requestJson == body.canonical && keys.add(row.originalKey)) { "原请求已变化" }
            digest.append(row)
        }
        check(digest.count == plan.requestCount && digest.finish() == plan.requestSetSha256) { "原请求集合摘要不一致" }
        return plan
    }
}
