package com.acrylic.outbound.pda.sync

import com.acrylic.outbound.pda.data.*
import com.acrylic.outbound.pda.network.*
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.*
import org.json.*
import java.util.concurrent.Callable

class AuditRepository internal constructor(private val db: PdaDatabase, private val currentSession: () -> JSONObject,
    private val request: (String, String, JSONObject) -> JSONObject) {
    internal constructor(db: PdaDatabase, current: () -> JSONObject, request: (String, String) -> JSONObject)
        : this(db, current, { body, epoch, _ -> request(body, epoch) })
    constructor(db: PdaDatabase, session: PdaSession) : this(db, session::requireSession,
        { body, epoch, auth -> session.call("pda/audit/events", body = JSONObject(body), epoch = epoch, expected = auth) })

    fun upload(): String = syncAudit().message
    @Synchronized fun syncAudit(run: SyncRun = SyncRun()): SyncResult {
        val auth = JSONObject(currentSession().toString())
        val namespace = AuditCheckpoint(auth.getString("serverRecoveryEpoch"), auth.getString("deviceId"))
        repeat(run.batches) {
            val pending = run.commit { db.runInTransaction(Callable { run.identity(auth, currentSession); prepare(namespace) }) }
                ?: return SyncResult("操作记录已补传")
            run.identity(auth, currentSession)
            val result = try { request(checkNotNull(pending.pendingJson), namespace.recoveryEpoch, auth) }
            catch (error: ApiFailure) {
                if (error.httpStatus == 404) return SyncResult("当前后台尚未提供操作记录补传，原记录已保留", supported = false)
                throw error
            }
            run.commit { db.runInTransaction { run.identity(auth, currentSession); acknowledge(pending, result) } }
        }
        return SyncResult("操作记录仍在补传，下次联网刷新将继续", more = true)
    }
    private fun prepare(namespace: AuditCheckpoint): AuditCheckpoint? = db.runInTransaction(Callable {
        val state = db.audit().checkpoint(namespace.recoveryEpoch, namespace.originDeviceId) ?: namespace
        if (state.pendingJson != null) return@Callable state
        val rows = DeviceAuditTrail(db).page(state.originDeviceId, state.acknowledgedThrough)
        if (rows.isEmpty()) return@Callable null
        var next = state.acknowledgedThrough
        rows.forEach { check(it.getString("localSeq").toLong() == Math.addExact(next, 1)) { "本机操作记录存在序号缺口，已保留原记录，请联系主管核对" }; next++ }
        val body = JSONObject().put("originDeviceId", state.originDeviceId)
            .put("events", JSONArray(rows))
        state.copy(pendingJson = SubmissionJson.canonical(body)).also { db.audit().save(it) }
    })
    private fun acknowledge(expected: AuditCheckpoint, result: JSONObject) {
        val raw = checkNotNull(expected.pendingJson)
        check(result.getString("recoveryEpoch") == expected.recoveryEpoch && result.getString("originDeviceId") == expected.originDeviceId &&
            result.getString("batchSha256") == JwtVerifier.sha(raw.toByteArray(Charsets.UTF_8))) { "操作记录确认不属于原批次" }
        val sequence = result.get("acknowledgedThrough")
        check(sequence is String && sequence.matches(Regex("0|[1-9][0-9]*"))) { "操作记录确认序号无效" }
        val through = sequence.toLong()
        val events = JSONObject(raw).getJSONArray("events")
        val last = events.getJSONObject(events.length() - 1).getString("localSeq").toLong()
        check(through >= last && through > expected.acknowledgedThrough) { "云端尚未连续确认原批次，已保留原请求，请重试或联系主管" }
        db.runInTransaction {
            check(db.audit().checkpoint(expected.recoveryEpoch, expected.originDeviceId) == expected) { "补传进度已变化，请重新刷新" }
            check(db.audit().coverage(expected.originDeviceId, expected.acknowledgedThrough, through) == through - expected.acknowledgedThrough) {
                "云端确认超出本机连续记录，已保留原请求，请联系主管核对"
            }
            db.audit().save(expected.copy(acknowledgedThrough = through, pendingJson = null))
        }
    }
}
