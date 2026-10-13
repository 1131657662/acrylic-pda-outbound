package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.security.TimeSample
import com.acrylic.outbound.pda.work.WorkDevice
import org.json.JSONObject
import java.util.UUID

class TemporaryWorkStore(val db: PdaDatabase, val connection: TemporaryConnection,
    internal val continuationDevelopment: Boolean = com.acrylic.outbound.pda.BuildConfig.TEMPORARY_CONTINUATION_DEVELOPMENT,
    internal val handoverDevelopment: Boolean = com.acrylic.outbound.pda.BuildConfig.TEMPORARY_HANDOVER_DEVELOPMENT,
    val sample: () -> TimeSample) {
    constructor(db: PdaDatabase, connection: TemporaryConnection, continuationDevelopment: Boolean, sample: () -> TimeSample) :
        this(db, connection, continuationDevelopment, com.acrylic.outbound.pda.BuildConfig.TEMPORARY_HANDOVER_DEVELOPMENT, sample)
    constructor(db: PdaDatabase, connection: TemporaryConnection, sample: () -> TimeSample) :
        this(db, connection, com.acrylic.outbound.pda.BuildConfig.TEMPORARY_CONTINUATION_DEVELOPMENT, sample)
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
        if (db.localDraftStops().get(id) != null || db.temporaryScans().localStopCount(id) != 0)
            check(draft.state == "EVIDENCE_STOPPED") { "停止证据与本机状态冲突，请保留原资料" }
        if (draft.planSha256.isNotEmpty() || draft.state == "SCANNING")
            check(draft.planSha256 == TemporaryPlanDigest.digest(draft, db.temporaryDrafts().lines(id))) { "原临时计划摘要不一致，请停扫并保留资料" }
        if (db.temporaryHandoverActivations().caseForTarget(id) != null) TemporaryHandoverBaseline(this).read(draft)
        else if (draft.workRound > 1) TemporaryContinuationBaseline(this).read(draft, draft.state != "CONTINUATION_BLOCKED")
        if (requireTime) TemporaryVerifier.time(catalog.manifest, connection.now())
        return draft
    }
    fun total(id: String) = if (isHandover(id)) TemporaryHandoverTotals(this).total(id) else TemporaryContinuationTotals(this).total(id)
    fun canScan(draft: TemporaryDraft): Boolean = draft.canScan(
        if (db.temporaryHandoverActivations().caseForTarget(draft.clientOrderId) != null) handoverDevelopment else continuationDevelopment)
    fun products(id: String) = if (isHandover(id)) TemporaryHandoverTotals(this).products(id) else TemporaryContinuationTotals(this).products(id)
    fun page(id: String, after: Long) = if (isHandover(id)) TemporaryHandoverTotals(this).page(id, after) else TemporaryContinuationTotals(this).page(id, after)
    internal fun isHandover(id: String) = db.temporaryHandoverActivations().caseForTarget(id) != null
    private fun identity(draft: TemporaryDraft, catalog: TemporaryCatalog) {
        val auth = access.snapshot(); access.request(catalog.request, auth)
        TemporaryVerifier.identity(catalog.manifest, auth)
        check(draft.creatorDeviceId == catalog.request.deviceId && draft.operatorId == catalog.request.userId &&
            draft.warehouseId == catalog.request.warehouseId && draft.authorizationId == catalog.preparation.authorizationId) { "原临时单来源不一致" }
        check(db.temporary().request(catalog.request.key) == catalog.request &&
            db.temporary().prepared(catalog.request.key)?.copy(ackJson = catalog.preparation.ackJson) == catalog.preparation) { "原临时资料已变化，请重新核验" }
    }
    fun requireNoOtherInput(id: String) {
        check(db.work().unresolvedCount() == 0 && db.temporaryScans().unresolved(id) == 0 &&
            db.independentScans().unresolved() == 0) { "另一来源仍有未处理输入，请先恢复原作业" }
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
    fun stopEvent(draft: TemporaryDraft, eventId: String, submissionId: String): Long {
        check(db.inTransaction() && draft.state == "FROZEN")
        val device = checkNotNull(db.work().device(draft.creatorDeviceId))
        val time = sample()
        db.temporaryScans().insertEvent(TemporaryEvent(eventId, draft.clientOrderId, null,
            draft.creatorDeviceId, draft.operatorId, device.nextEventSeq, "STOP", submissionId,
            time.wall, time.elapsed, time.boot))
        db.work().updateDevice(device.copy(nextEventSeq = Math.addExact(device.nextEventSeq, 1)))
        return device.nextEventSeq
    }
}
