package com.acrylic.outbound.pda.submission

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.network.*
import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONObject

/** One instance per Application: UI and WorkManager serialize on the same monitor. */
class SubmissionRepository(private val db: PdaDatabase, private val session: PdaSession) {
    @Synchronized fun upload(id: String): LocalSubmission {
        val local = checkNotNull(db.submissions().get(id)); if (local.state in setOf("SYNCED", "ABORTED", "REJECTED")) return local
        val prepared = checkNotNull(db.preparations().order(local.assignmentId)); val current = session.requireSession()
        check(current.getString("userId") == prepared.operatorId && current.getString("deviceId") == prepared.deviceId) { "须原人员在原设备上传" }
        check(JwtVerifier.sha(local.payload.toByteArray(Charsets.UTF_8)) == local.sha256) { "冻结正文摘要损坏，请停止并保留数据" }
        db.submissions().progress(id, "RESULT_UNKNOWN", local.receipt, "正在核对原批次", local.autoRetry)
        try {
            val queried = try { session.call("pda/submissions/$id") } catch (e: ApiFailure) { if (e.code == "SUBMISSION_NOT_FOUND") null else throw e }
            val response = if (queried == null || queried.getString("status") in setOf("REGISTERED", "RECEIVING")) SubmissionTransport(session).send(local) else queried
            val state = ReceiptVerifier.verify(local, response, JSONObject(prepared.signingKeyJson))
            val retry = state in setOf("REGISTERED", "RECEIVING", "REVIEW_REQUIRED") && local.autoRetry
            db.submissions().progress(id, state, response.toString(), "", retry)
        } catch (error: Exception) {
            val retry = error is java.io.IOException || error is ApiFailure && error.code in setOf("INTERNAL_ERROR", "LOGIN_RATE_LIMIT", "SERVER_RECOVERY_REQUIRED")
            db.submissions().progress(id, if (retry) "RESULT_UNKNOWN" else "ACTION_REQUIRED", local.receipt,
                error.message ?: "结果未知，保留原正文核对", retry && local.autoRetry)
            throw error
        }
        return checkNotNull(db.submissions().get(id))
    }
}
