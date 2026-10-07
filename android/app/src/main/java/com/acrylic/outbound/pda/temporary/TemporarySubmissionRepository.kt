package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.network.*
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.sync.RecoveryProtection
import com.acrylic.outbound.pda.update.UpdateGate
import org.json.JSONObject

/** Application singleton serializes manual/worker recovery of the original durable submission. */
class TemporarySubmissionRepository(private val db: PdaDatabase, private val connection: TemporaryConnection,
    private val continuationDevelopment: Boolean = com.acrylic.outbound.pda.BuildConfig.TEMPORARY_CONTINUATION_DEVELOPMENT) {
    @Synchronized fun upload(id: String): TemporarySubmission = UpdateGate.business {
        val local = checkNotNull(db.temporarySubmissions().get(id))
        if (local.state == "ABORTED") return@business local
        val expected = JSONObject(checkNotNull(connection.current()) { "请先登录" }.toString())
        checkIdentity(local, expected)
        check(JwtVerifier.sha(local.payload.toByteArray(Charsets.UTF_8)) == local.sha256) { "原临时冻结正文损坏，请保留数据" }
        db.temporarySubmissions().progress(id, "RESULT_UNKNOWN", local.receipt, "正在核对原临时批次", local.autoRetry)
        try {
            val previous = TemporarySuccessRepository(db).read(id, signingKey(local))
            if (previous != null) save(local, expected, previous, "SYNCED")
            else accept(local, expected, recover(local, expected))
        } catch (error: Exception) { failed(local, error); throw error }
        checkNotNull(db.temporarySubmissions().get(id))
    }
    @Synchronized fun abort(id: String, reason: String): TemporarySubmission = UpdateGate.business {
        val local = checkNotNull(db.temporarySubmissions().get(id)); if (local.state == "ABORTED") return@business local
        check(JSONObject(local.payload).getString("workRound") == "1") { "续作取消释放政策未定义，原记录保留" }
        if (local.state == "SYNCED" || db.temporarySuccess().evidence(id) != null) return@business upload(id)
        val value = reason.trim(); check(value.isNotEmpty() && value.length <= 500) { "取消须填写1～500字原因" }
        db.runInTransaction {
            checkIdentity(local, checkNotNull(connection.current()))
            check(local.abortReason == null || local.abortReason == value) { "须恢复原取消请求" }
            db.temporarySubmissions().abortIntent(id, value)
        }
        upload(id)
    }
    @Synchronized fun stopContinuation(id: String, reason: String): TemporarySubmission = UpdateGate.business {
        check(continuationDevelopment) { "续作停止开发入口未启用" }
        val local = checkNotNull(db.temporarySubmissions().get(id))
        check(JSONObject(local.payload).getString("workRound").toLong() > 1)
        if (local.state == "SYNCED" || db.temporarySuccess().evidence(id) != null) return@business upload(id)
        val value = reason.trim(); check(value.isNotEmpty() && value.length <= 500) { "停止须填写1～500字原因" }
        db.runInTransaction {
            checkIdentity(local, checkNotNull(connection.current()))
            TemporaryContinuationStopRequest(db).body(local, value, signingKey(local))
            check(local.abortReason == null || local.abortReason == value) { "须恢复原停止请求" }
            db.temporarySubmissions().abortIntent(id, value)
            db.temporarySubmissions().queue(id)
        }
        upload(id)
    }
    private fun recover(local: TemporarySubmission, expected: JSONObject): TemporarySubmissionResponses.Reply {
        val responses = TemporarySubmissionResponses(db, connection, expected, local)
        val continuation = JSONObject(local.payload).getString("workRound").toLong() > 1
        val path = if (continuation) "pda/temporary-orders/continuation-submissions/${local.id}" else "pda/submissions/${local.id}"
        val queried = try { responses.receive(path) }
            catch (error: ApiFailure) { if (error.code == "SUBMISSION_NOT_FOUND") null else throw error }
        val transport = TemporarySubmissionTransport(connection, expected, responses)
        if (queried?.data?.getString("status") in setOf("ABORTED", "SUCCEEDED")) return checkNotNull(queried)
        if (continuation && local.abortReason != null) {
            check(continuationDevelopment) { "续作停止开发入口未启用" }
            return responses.receive("$path/stop", TemporaryContinuationStopRequest(db).body(local, local.abortReason, signingKey(local)))
        }
        if (local.abortReason != null || queried == null || queried.data.getString("status") in setOf("REGISTERED", "RECEIVING")) return transport.send(local)
        return queried
    }
    private fun accept(local: TemporarySubmission, expected: JSONObject, reply: TemporarySubmissionResponses.Reply) {
        db.runInTransaction {
            checkIdentity(local, expected)
            val state = if (reply.data.getString("status") == "SUCCEEDED") {
                TemporarySuccessRepository(db).verify(local.id, reply.responseSha256, signingKey(local)); "SYNCED"
            } else {
                check(db.temporarySuccess().evidence(local.id) == null) { "已存成功原件，不能改写为其他状态" }
                TemporarySubmissionReceipt.verify(local, reply.data, signingKey(local)).also { state ->
                    if (state == "ABORTED" && JSONObject(local.payload).getString("workRound").toLong() > 1)
                        TemporaryContinuationStopRequest(db).verify(local, reply.data, signingKey(local))
                }
            }
            save(local, expected, reply.data, state)
        }
    }
    private fun save(local: TemporarySubmission, expected: JSONObject, receipt: JSONObject, state: String) = db.runInTransaction {
        checkIdentity(local, expected)
        db.temporarySubmissions().progress(local.id, state, receipt.toString(), "", state !in setOf("SYNCED", "ABORTED") && local.autoRetry)
    }
    private fun signingKey(local: TemporarySubmission): JSONObject {
        val preparation = checkNotNull(db.temporary().byAuthorization(JSONObject(local.payload).getString("authorizationId")))
        return JSONObject(checkNotNull(db.temporary().request(preparation.requestKey)).signingKeyJson)
    }
    @Synchronized fun checked(id: String): TemporarySubmission {
        val local = checkNotNull(db.temporarySubmissions().get(id))
        if (local.state != "SYNCED") return local
        try {
            val receipt = checkNotNull(TemporarySuccessRepository(db).read(id, signingKey(local))) { "缺少已核验成功原件" }
            check(com.acrylic.outbound.pda.submission.SubmissionJson.canonical(receipt) ==
                com.acrylic.outbound.pda.submission.SubmissionJson.canonical(JSONObject(checkNotNull(local.receipt)))) { "本地成功显示与原件不一致" }
        }
        catch (error: Exception) { failed(local, error) }
        return checkNotNull(db.temporarySubmissions().get(id))
    }
    private fun checkIdentity(local: TemporarySubmission, expected: JSONObject) {
        SessionIdentity.requireSame(expected, connection.current()); RecoveryProtection(db).requireNormal()
        val current = checkNotNull(connection.current()); check(!current.has("blockedReason")) { "当前会话已停用，请重新登录核对" }
        for (field in listOf("userAuthVersion", "deviceVersion")) check(expected.getString(field) == current.getString(field)) { "会话授权版本已变化，请重新核对" }
        val draft = checkNotNull(db.temporaryDrafts().get(local.clientOrderId)); val body = JSONObject(local.payload)
        check(draft.state == "FROZEN" && current.getString("deviceId") == draft.creatorDeviceId && current.getString("userId") == draft.operatorId &&
            current.getString("warehouseId") == draft.warehouseId && current.getString("serverRecoveryEpoch") == body.getString("sourceRecoveryEpoch")) { "须原人员、原设备及原恢复代次核对临时批次" }
        check(body.getString("authorizationId") == draft.authorizationId && body.getString("submissionId") == local.id &&
            body.getJSONObject("source").getString("clientOrderId") == draft.clientOrderId) { "原临时冻结身份不一致" }
        TemporaryContinuationUploadValidation.verify(db, connection, local, body)
        val saved = checkNotNull(db.temporarySubmissions().get(local.id))
        check(saved.sha256 == local.sha256 && saved.payload == local.payload && saved.abortReason == local.abortReason) { "原临时请求已变化" }
    }
    private fun failed(local: TemporarySubmission, error: Exception) {
        val retry = error is java.io.IOException || error is ApiFailure && error.code in setOf("INTERNAL_ERROR", "LOGIN_RATE_LIMIT", "SERVER_RECOVERY_REQUIRED")
        db.temporarySubmissions().progress(local.id, if (retry) "RESULT_UNKNOWN" else "ACTION_REQUIRED", local.receipt,
            error.message ?: "结果未知，请核对原临时批次", retry && local.autoRetry)
    }
}
