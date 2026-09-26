package com.acrylic.outbound.pda.retention

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.network.*
import com.acrylic.outbound.pda.submission.*
import com.acrylic.outbound.pda.sync.SyncRun
import com.acrylic.outbound.pda.sync.SyncResult
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.time.Instant

class SubmissionArchiveRepository internal constructor(private val db: PdaDatabase, private val currentSession: () -> JSONObject,
    private val now: () -> Instant, private val request: (String, JSONObject, String) -> JSONObject) {
    constructor(db: PdaDatabase, session: PdaSession) : this(db, session::requireSession, session::now,
        { id, body, epoch -> session.call("pda/submissions/$id/archive-confirmation", body, epoch) })

    @Synchronized fun refresh(run: SyncRun = SyncRun()): String = try {
        refreshBackground(run).message
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        "归档尚未核验，本机原记录已保留：${error.message ?: "请联网刷新重试"}"
    }

    @Synchronized fun refreshBackground(run: SyncRun): SyncResult {
        try {
            val auth = JSONObject(currentSession().toString()); run.identity(auth, currentSession); requireNormal(auth)
            val pending = db.archives().pending(auth.getString("userId"), auth.getString("deviceId"), auth.getString("serverRecoveryEpoch"))
            pending.forEach { run.identity(auth, currentSession); confirm(it, auth, run) }
            return if (pending.size < 10) SyncResult("成功批次归档已核对，本机记录保留")
                else SyncResult("成功批次归档分批核对中，下次刷新继续", more = true)
        } catch (error: ApiFailure) {
            if (error.httpStatus == 404 && error.code != "SUBMISSION_NOT_FOUND")
                return SyncResult("当前后台尚未提供归档确认，本机原记录已保留", supported = false)
            throw error
        }
    }
    private fun confirm(id: String, auth: JSONObject, run: SyncRun) {
        val local = checkNotNull(db.submissions().get(id)); val prepared = checkNotNull(db.preparations().order(local.assignmentId))
        val epoch = auth.getString("serverRecoveryEpoch"); val body = JSONObject(local.payload)
        check(prepared.operatorId == auth.getString("userId") && prepared.deviceId == auth.getString("deviceId") &&
            body.getString("sourceRecoveryEpoch") == epoch && body.getString("freezeDeviceId") == prepared.deviceId) { "原批次身份或恢复代次不符，请先对账" }
        val expected = SubmissionArchiveVerifier.request(local, JSONObject(prepared.signingKeyJson))
        run.identity(auth, currentSession)
        val response = request(id, JSONObject(expected.toString()), epoch); val key = auth.getJSONObject("signingKey")
        SubmissionArchiveVerifier.verify(local, auth, expected, response, key)
        val row = SubmissionArchive(id, epoch, local.sha256, expected.getString("receiptSha256"),
            SubmissionJson.canonical(response), SubmissionJson.canonical(key), now().toEpochMilli())
        run.commit { db.runInTransaction {
            requireNormal(auth)
            check(db.submissions().get(id) == local && db.preparations().order(local.assignmentId) == prepared) { "归档期间本机证据变化，请重新核对" }
            val old = db.archives().get(id, epoch)
            if (old == null) db.archives().insert(row)
            else check(old.confirmationJson == row.confirmationJson) { "归档凭据变化，请联系管理员核对" }
        } }
    }
    private fun requireNormal(expected: JSONObject) {
        val current = currentSession()
        for (field in listOf("userId", "deviceId", "warehouseId", "serverRecoveryEpoch"))
            check(current.getString(field) == expected.getString(field)) { "身份或恢复代次变化，请重新登录核对" }
        check(!current.has("blockedReason")) { current.optString("blockedReason") }
        val recovery = db.sync().recovery()
        check(recovery?.mode == "NORMAL" && recovery.observedServerEpoch == expected.getString("serverRecoveryEpoch")) { "服务器恢复对账期间暂不确认归档" }
    }
}
