package com.acrylic.outbound.pda.retention

import com.acrylic.outbound.pda.data.*
import com.acrylic.outbound.pda.network.*
import com.acrylic.outbound.pda.submission.*
import com.acrylic.outbound.pda.sync.SyncRun
import com.acrylic.outbound.pda.sync.SyncResult
import org.json.*
import java.util.concurrent.Callable

class AuditArchiveRepository internal constructor(private val db: PdaDatabase, private val current: () -> JSONObject,
    private val request: (Boolean, JSONObject, String, JSONObject) -> JSONObject) {
    internal constructor(db: PdaDatabase, current: () -> JSONObject, request: (Boolean, JSONObject, String) -> JSONObject)
        : this(db, current, { restoring, body, epoch, _ -> request(restoring, body, epoch) })
    constructor(db: PdaDatabase, session: PdaSession) : this(db, session::requireSession,
        { restoring, body, epoch, auth -> session.call("pda/audit/archives" + if (restoring) "/restore" else "", body, epoch, expected = auth) })

    @Synchronized fun restore(run: SyncRun = SyncRun()): Boolean {
        run.check()
        val auth = JSONObject(current().toString()); val epoch = auth.getString("serverRecoveryEpoch"); val device = auth.getString("deviceId")
        run.identity(auth, current)
        val oldEpoch = db.auditArchives().previousEpoch(epoch, device) ?: return true
        val old = AuditArchiveVerifier.prefix(db.auditArchives().rows(oldEpoch, device), auth.getString("warehouseId"))
        // Seed only a verified contiguous prefix, and never acknowledge or discard an in-flight audit request.
        old.filter { db.auditArchives().get(epoch, device, it.afterSeq)?.proofJson == null }.take(run.batches).forEach { row ->
            run.identity(auth, current)
            val existing = db.auditArchives().get(epoch, device, row.afterSeq)
            if (existing?.proofJson == null) {
                val pending = existing ?: row.copy(recoveryEpoch = epoch, restoring = true, proofJson = null, signingKeyJson = null,
                    requestJson = SubmissionJson.canonical(JSONObject().put("archive", JSONObject(checkNotNull(row.proofJson)))))
                run.commit { run.identity(auth, current); savePending(pending, auth) }; confirm(pending, auth, run)
            }
        }
        run.commit { run.identity(auth, current); seed(auth) }
        val currentRows = AuditArchiveVerifier.prefix(db.auditArchives().rows(epoch, device), auth.getString("warehouseId"))
        return (currentRows.lastOrNull()?.throughSeq ?: 0) >= (old.lastOrNull()?.throughSeq ?: 0)
    }
    fun archive(): String = syncArchive().message
    @Synchronized fun syncArchive(run: SyncRun = SyncRun()): SyncResult {
        val auth = JSONObject(current().toString())
        repeat(run.batches) {
            val pending = run.commit { run.identity(auth, current); prepare(auth) } ?: return SyncResult("操作记录归档已核验，本机原记录保留")
            try { confirm(pending, auth, run) }
            catch (error: ApiFailure) { if (error.httpStatus == 404) return SyncResult("当前后台尚未提供审计归档，本机原记录保留", supported = false); throw error }
        }
        return SyncResult("操作记录归档分批核对中，下次刷新继续", more = true)
    }
    private fun prepare(auth: JSONObject): AuditArchive? = db.runInTransaction(Callable {
        identity(auth);val epoch = auth.getString("serverRecoveryEpoch"); val device = auth.getString("deviceId")
        val rows = db.auditArchives().rows(epoch, device)
        val prefix = AuditArchiveVerifier.prefix(rows, auth.getString("warehouseId")); val after = prefix.lastOrNull()?.throughSeq ?: 0L
        rows.firstOrNull { it.proofJson == null }?.let { check(it.afterSeq == after); return@Callable it }
        val checkpoint = db.audit().checkpoint(epoch, device) ?: return@Callable null
        val through = minOf(checkpoint.acknowledgedThrough, after + 200)
        if (through <= after) return@Callable null
        check(rows.size < 1024) { "审计归档达到当前资源限额，请保留原记录联系管理员" }
        val events = JSONArray(DeviceAuditTrail(db).page(device, after).filter { it.getString("localSeq").toLong() <= through })
        val payload = AuditArchiveVerifier.payload(device, after, through, events)
        val hash = AuditArchiveVerifier.digest(payload)
        val body = JSONObject().put("originDeviceId", device).put("afterSeq", after.toString()).put("throughSeq", through.toString()).put("payloadSha256", hash)
        AuditArchive(epoch, device, after, through, hash, SubmissionJson.canonical(body)).also { db.auditArchives().insert(it) }
    })
    private fun savePending(row: AuditArchive, auth: JSONObject) = db.runInTransaction {
        identity(auth);val existing = db.auditArchives().get(row.recoveryEpoch, row.originDeviceId, row.afterSeq)
        if (existing == null) db.auditArchives().insert(row) else check(existing == row) { "审计归档原请求变化" }
    }
    private fun confirm(row: AuditArchive, auth: JSONObject, run: SyncRun) {
        run.identity(auth, current)
        val proof = request(row.restoring, JSONObject(row.requestJson), row.recoveryEpoch, auth)
        val key = auth.getJSONObject("signingKey")
        AuditArchiveVerifier.verify(row, proof, key, auth.getString("warehouseId"))
        if (row.restoring) check(proof.getString("byteCount") == JSONObject(row.requestJson)
            .getJSONObject("archive").getString("byteCount")) { "恢复审计长度与原归档不一致" }
        run.commit { db.runInTransaction {
            identity(auth);check(db.auditArchives().get(row.recoveryEpoch, row.originDeviceId, row.afterSeq) == row) { "审计归档进度变化" }
            val local = DeviceAuditTrail(db).page(row.originDeviceId, row.afterSeq).filter { it.getString("localSeq").toLong() <= row.throughSeq }
            if (!row.restoring || local.isNotEmpty()) {
                val payload = AuditArchiveVerifier.payload(row.originDeviceId, row.afterSeq, row.throughSeq, JSONArray(local))
                check(AuditArchiveVerifier.digest(payload) == row.payloadSha256) { "本机审计归档内容已变化" }
                check(SubmissionJson.canonical(payload).toByteArray(Charsets.UTF_8).size.toString() == proof.getString("byteCount")) {
                    "本机审计字节数与归档不一致"
                }
            }
            db.auditArchives().update(row.copy(proofJson = SubmissionJson.canonical(proof), signingKeyJson = SubmissionJson.canonical(key)))
        } }
    }
    private fun seed(auth: JSONObject) = db.runInTransaction {
        identity(auth);val epoch = auth.getString("serverRecoveryEpoch");val device = auth.getString("deviceId")
        val through = AuditArchiveVerifier.prefix(db.auditArchives().rows(epoch, device), auth.getString("warehouseId")).lastOrNull()?.throughSeq ?: 0
        val checkpoint = db.audit().checkpoint(epoch, device) ?: AuditCheckpoint(epoch, device)
        if (checkpoint.pendingJson == null && through > checkpoint.acknowledgedThrough) db.audit().save(checkpoint.copy(acknowledgedThrough = through))
    }
    private fun identity(auth: JSONObject) {
        val latest = current()
        SessionIdentity.requireSame(auth, latest)
        check(!latest.has("blockedReason")) { latest.optString("blockedReason") }
        check(db.sync().recovery()?.observedServerEpoch == auth.getString("serverRecoveryEpoch")) { "恢复代次尚未观察确认" }
        // Uploading evidence may run while local reconciliation remains active; never clear that barrier here.
    }
}
