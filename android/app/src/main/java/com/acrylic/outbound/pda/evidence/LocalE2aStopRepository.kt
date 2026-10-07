package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.temporary.TemporaryDraft
import com.acrylic.outbound.pda.temporary.TemporarySubmission
import com.acrylic.outbound.pda.temporary.TemporaryWorkStore
import com.acrylic.outbound.pda.update.UpdateGate
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Callable

class LocalE2aStopRepository(private val store: TemporaryWorkStore) {
    fun record(sourceId: String): LocalE2aStop = UpdateGate.business {
        store.load(sourceId)
        store.db.runInTransaction(Callable {
            val draft = store.check(sourceId, false)
            val frozen = checkNotNull(store.db.temporarySubmissions().forOrder(sourceId))
            requireFrozen(draft, frozen)
            val saved = checkNotNull(store.db.localE2aStops().get(sourceId)) {
                "原冻结没有同事务停止证明，不能追补 STOP，请保留资料核对"
            }
            verifyExisting(saved, draft, frozen)
        })
    }

    fun createAtFreeze(draft: TemporaryDraft, frozen: TemporarySubmission) {
        check(store.db.inTransaction() && store.db.temporaryDrafts().get(draft.clientOrderId) == draft)
        check(store.db.temporarySubmissions().forOrder(draft.clientOrderId) == frozen)
        val payload = requireFrozen(draft, frozen)
        check(store.db.localE2aStops().get(draft.clientOrderId) == null &&
            store.db.temporaryScans().stopCount(draft.clientOrderId) == 0)
        val stopEventId = UUID.randomUUID().toString()
        val stopSeq = store.stopEvent(draft, stopEventId, frozen.id)
        val boundary = E2aDeviceCoverage(store.db).verify(draft.creatorDeviceId)
        check(boundary.auditThroughSeq == stopSeq)
        val scanCount = Math.addExact(store.db.scans().deviceCount(draft.creatorDeviceId),
            store.db.temporaryScans().deviceScanCount(draft.creatorDeviceId))
        val archives = store.db.auditArchives().completed(draft.creatorDeviceId,
            payload.getString("sourceRecoveryEpoch").toLong())
        check(archives.size <= 4096)
        store.db.localE2aStops().insert(LocalE2aStop(draft.clientOrderId, frozen.id,
            draft.authorizationId, draft.warehouseId, draft.operatorId, draft.creatorDeviceId,
            payload.getString("sourceRecoveryEpoch").toLong(), payload.getString("inventorySnapshotId"),
            UUID.randomUUID().toString(), UUID.randomUUID().toString(), stopEventId,
            boundary.captureHighWatermark, boundary.auditThroughSeq, scanCount, archives.size.toLong(), draft.workRound))
        LocalE2aSnapshot(store.db).save(draft.clientOrderId, frozen, boundary, archives)
        LocalE2aTransferBuilder(store.db).save(checkNotNull(store.db.localE2aStops().get(draft.clientOrderId)),
            store.connection::stopProof)
        store.check(draft.clientOrderId, false)
    }

    private fun requireFrozen(draft: TemporaryDraft, frozen: TemporarySubmission): JSONObject {
        check(draft.state == "FROZEN" && frozen.clientOrderId == draft.clientOrderId)
        check(JwtVerifier.sha(frozen.payload.toByteArray(Charsets.UTF_8)) == frozen.sha256)
        val payload = JSONObject(frozen.payload)
        val source = store.source(draft.clientOrderId)
        check(payload.getString("submissionId") == frozen.id &&
            payload.getString("workRound") == draft.workRound.toString() &&
            payload.getJSONObject("source").getString("kind") == "TEMP" &&
            payload.getJSONObject("source").getString("clientOrderId") == draft.clientOrderId &&
            payload.getString("authorizationId") == draft.authorizationId &&
            payload.getString("freezeDeviceId") == draft.creatorDeviceId &&
            payload.getString("sourceRecoveryEpoch") == source.request.recoveryEpoch &&
            payload.getString("inventorySnapshotId") == source.manifest.getString("inventorySnapshotId"))
        val epoch = payload.getString("sourceRecoveryEpoch")
        check(epoch.matches(Regex("[1-9][0-9]*")) && epoch.toLong() in 1..9007199254740991L)
        return payload
    }

    private fun verifyExisting(row: LocalE2aStop, draft: TemporaryDraft,
        frozen: TemporarySubmission): LocalE2aStop {
        val source = store.source(draft.clientOrderId)
        check(row.submissionId == frozen.id && row.authorizationId == draft.authorizationId && row.workRound == draft.workRound &&
            row.warehouseId == draft.warehouseId && row.originalOperatorId == draft.operatorId &&
            row.freezeDeviceId == draft.creatorDeviceId &&
            row.sourceRecoveryEpoch.toString() == source.request.recoveryEpoch &&
            row.snapshotId == source.manifest.getString("inventorySnapshotId"))
        val event = checkNotNull(store.db.temporaryScans().event(row.stopEventId))
        check(store.db.temporaryScans().stopCount(row.clientOrderId) == 1)
        check(event.clientOrderId == row.clientOrderId && event.originDeviceId == row.freezeDeviceId &&
            event.operatorId == row.originalOperatorId && event.localSeq == row.auditThroughSeq &&
            event.type == "STOP" && event.detail == row.submissionId)
        val current = E2aDeviceCoverage(store.db).verify(row.freezeDeviceId)
        check(current.auditThroughSeq >= row.auditThroughSeq &&
            current.captureHighWatermark >= row.captureHighWatermark)
        LocalE2aSnapshot(store.db).verify(row, frozen)
        return row
    }
}
