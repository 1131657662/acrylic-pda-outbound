package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import java.util.concurrent.Callable

/** Historical original-response consumer; product projection belongs to TemporarySubmissionRepository. */
class TemporarySuccessRepository(private val db: PdaDatabase) {
    fun retain(submissionId: String, original: ByteArray): String {
        check(original.size in 1..262144) { "成功原响应长度无效" }
        val bytes = original.copyOf(); val hash = JwtVerifier.sha(bytes)
        db.runInTransaction {
            checkNotNull(db.temporarySubmissions().get(submissionId)) { "缺少原冻结正文" }
            val previous = db.temporarySuccess().response(submissionId, hash)
            if (previous == null) db.temporarySuccess().insert(TemporarySuccessResponse(submissionId, hash, bytes))
            else check(previous.responseBytes.contentEquals(bytes)) { "同摘要原响应发生冲突" }
        }
        return hash
    }

    fun verify(submissionId: String, responseSha256: String, trustedKey: JSONObject, mappedOrderId: String? = null): JSONObject = try {
        db.runInTransaction(Callable {
            val dao = db.temporarySuccess()
            val local = checkNotNull(db.temporarySubmissions().get(submissionId))
            val raw = checkNotNull(dao.response(submissionId, responseSha256))
            check(JwtVerifier.sha(raw.responseBytes) == responseSha256) { "耐久原响应摘要不一致" }
            val previous = dao.evidence(submissionId)
            val receipt = TemporarySuccessVerifier.verify(local, raw.responseBytes, trustedKey, mappedOrderId ?: previous?.orderId)
            val digest = JwtVerifier.sha(SubmissionJson.canonical(receipt).toByteArray(Charsets.UTF_8))
            if (previous == null) dao.accept(TemporarySuccessEvidence(submissionId, responseSha256, digest,
                receipt.getString("orderId"), SubmissionJson.canonical(trustedKey)))
            else check(previous.receiptSha256 == digest && previous.orderId == receipt.getString("orderId") &&
                previous.signingKeyJson == SubmissionJson.canonical(trustedKey)) {
                "原提交已有不同成功回执，保留冲突原件"
            }
            dao.result(submissionId, responseSha256, "VERIFIED", "")
            receipt
        })
    } catch (error: Exception) {
        db.temporarySuccess().result(submissionId, responseSha256, "REJECTED", "成功原回执未通过核对，原件已保留")
        throw error
    }

    fun read(submissionId: String, trustedKey: JSONObject): JSONObject? {
        val accepted = db.temporarySuccess().evidence(submissionId) ?: return null
        return verify(submissionId, accepted.responseSha256, trustedKey)
    }
}
