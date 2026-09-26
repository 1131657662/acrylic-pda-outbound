package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject

internal class LocalDraftReceiptStore(private val db: PdaDatabase, private val keys: List<JSONObject>) {
    fun verified(expected: LocalDraftReceiptVerifier.Expected): Boolean {
        check(db.inTransaction())
        val dao = db.localDraftTransfers(); val request = expected.request
        val saved = dao.receipt(request.originalKey) ?: return false
        if (saved.state == "RESULT_UNKNOWN") {
            check(saved.responseBytes == null && saved.signingKeyJson == null); return false
        }
        check(saved.state in setOf("RECEIVED", "VERIFIED"))
        val bytes = checkNotNull(saved.responseBytes)
        val key = keys.firstOrNull { runCatching { LocalDraftReceiptVerifier.verify(expected, bytes, it) }.isSuccess }
            ?: error("原回执不可核验，请保留原响应并核对可信历史公钥")
        val json = SubmissionJson.canonical(key)
        if (saved.state == "RECEIVED") {
            check(saved.signingKeyJson == null && dao.verified(request.originalKey, json) == 1)
        } else check(saved.signingKeyJson == json)
        return true
    }
    fun pending(request: LocalDraftRequest) {
        check(db.inTransaction()); val dao = db.localDraftTransfers(); val prior = dao.receipt(request.originalKey)
        if (prior == null) dao.insertReceipt(LocalDraftReceipt(request.originalKey, "RESULT_UNKNOWN"))
        else check(prior.state == "RESULT_UNKNOWN" && prior.responseBytes == null && prior.signingKeyJson == null)
    }
    fun received(request: LocalDraftRequest, bytes: ByteArray) {
        check(db.inTransaction() && bytes.size in 1..262144)
        check(db.localDraftTransfers().received(request.originalKey, bytes.copyOf()) == 1) { "原响应不得覆盖" }
    }
}
