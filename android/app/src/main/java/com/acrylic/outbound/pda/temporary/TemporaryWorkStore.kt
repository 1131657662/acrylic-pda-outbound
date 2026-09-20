package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.security.TimeSample
import com.acrylic.outbound.pda.work.WorkDevice
import org.json.JSONObject
import java.util.UUID

class TemporaryWorkStore(val db: PdaDatabase, val connection: TemporaryConnection, val sample: () -> TimeSample) {
    private val access = TemporaryAccess(db, connection)
    private val catalogs = mutableMapOf<String, TemporaryCatalog>()
    private var expectedSession: JSONObject? = null
    fun source(id: String): TemporaryCatalog = checkNotNull(catalogs[id]) { "请先打开原临时作业" }
    fun load(id: String): TemporaryDraft {
        val expected = access.snapshot()
        val draft = checkNotNull(db.temporaryDrafts().get(id)) { "原临时草稿不存在" }
        val prepared = checkNotNull(db.temporary().byAuthorization(draft.authorizationId))
        val request = checkNotNull(db.temporary().request(prepared.requestKey))
        // Replace the in-memory copy only after full signature and part validation succeeds.
        val catalog = TemporaryCatalog.load(db, request)
        access.check(expected); identity(draft, catalog); catalogs.clear(); catalogs[id] = catalog; expectedSession = expected
        return draft
    }
    fun check(id: String, requireTime: Boolean = true): TemporaryDraft {
        val draft = checkNotNull(db.temporaryDrafts().get(id)); val catalog = source(id)
        access.check(checkNotNull(expectedSession))
        identity(draft, catalog)
        if (draft.planSha256.isNotEmpty() || draft.state == "SCANNING")
            check(draft.planSha256 == TemporaryPlanDigest.digest(draft, db.temporaryDrafts().lines(id))) { "原临时计划摘要不一致，请停扫并保留资料" }
        if (requireTime) TemporaryVerifier.time(catalog.manifest, connection.now())
        return draft
    }
    private fun identity(draft: TemporaryDraft, catalog: TemporaryCatalog) {
        val auth = access.snapshot(); access.request(catalog.request, auth)
        TemporaryVerifier.identity(catalog.manifest, auth)
        check(draft.creatorDeviceId == catalog.request.deviceId && draft.operatorId == catalog.request.userId &&
            draft.warehouseId == catalog.request.warehouseId && draft.authorizationId == catalog.preparation.authorizationId) { "原临时单来源不一致" }
        check(db.temporary().request(catalog.request.key) == catalog.request &&
            db.temporary().prepared(catalog.request.key)?.copy(ackJson = catalog.preparation.ackJson) == catalog.preparation) { "原临时资料已变化，请重新核验" }
    }
    fun requireNoOtherInput(id: String) {
        check(db.work().unresolvedCount() == 0 && db.temporaryScans().unresolved(id) == 0) { "另一订单仍有未处理输入，请先恢复原订单" }
    }
    fun event(draft: TemporaryDraft, capture: String?, type: String, detail: String = ""): Long {
        check(db.inTransaction())
        db.work().insertDevice(WorkDevice(draft.creatorDeviceId))
        val device = checkNotNull(db.work().device(draft.creatorDeviceId)); val time = sample()
        db.temporaryScans().insertEvent(TemporaryEvent(UUID.randomUUID().toString(), draft.clientOrderId, capture,
            draft.creatorDeviceId, draft.operatorId, device.nextEventSeq, type, detail, time.wall, time.elapsed, time.boot))
        db.work().updateDevice(device.copy(nextEventSeq = Math.addExact(device.nextEventSeq, 1)))
        return device.nextEventSeq
    }
}
