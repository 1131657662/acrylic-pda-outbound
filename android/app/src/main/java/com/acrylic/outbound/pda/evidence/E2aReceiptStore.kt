package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject

internal class E2aReceiptStore(private val db: PdaDatabase, private val keys: List<JSONObject>) {
    fun verified(facts: E2aReceiptVerifier.Expected): Boolean {
        val saved = db.localE2aReceipts().get(facts.request.originalKey) ?: return false
        if (saved.state == "RESULT_UNKNOWN") {
            check(saved.state == "RESULT_UNKNOWN" && saved.responseBytes == null && saved.signingKeyJson == null)
            return false
        }
        check(saved.state in setOf("RECEIVED", "VERIFIED"))
        val bytes = checkNotNull(saved.responseBytes)
        val key = verify(facts, bytes)
        if (saved.state == "RECEIVED") {
            check(db.inTransaction() && saved.signingKeyJson == null)
            db.localE2aReceipts().update(saved.copy(state = "VERIFIED", signingKeyJson = key?.let(SubmissionJson::canonical)))
        } else check(saved.signingKeyJson == key?.let(SubmissionJson::canonical)) { "原回执验签公钥已变化" }
        return true
    }

    fun pending(request: LocalE2aRequest) {
        val dao = db.localE2aReceipts()
        val current = dao.get(request.originalKey)
        if (current == null) dao.insert(LocalE2aReceipt(request.originalKey, "RESULT_UNKNOWN"))
        else check(current.state == "RESULT_UNKNOWN" && current.responseBytes == null && current.signingKeyJson == null)
    }

    fun received(request: LocalE2aRequest, bytes: ByteArray) {
        check(bytes.size in 1..262144)
        val dao = db.localE2aReceipts()
        val saved = checkNotNull(dao.get(request.originalKey))
        check(saved.state == "RESULT_UNKNOWN" && saved.responseBytes == null)
        dao.update(saved.copy(state = "RECEIVED", responseBytes = bytes.copyOf(), error = ""))
    }

    private fun verify(facts: E2aReceiptVerifier.Expected, bytes: ByteArray): JSONObject? {
        if (facts.request.operation == "RAW") {
            E2aRawAcknowledgement.verify(facts.request, JSONObject(bytes.toString(Charsets.UTF_8)))
            return null
        }
        val pair = keys.firstNotNullOfOrNull { key ->
            runCatching { E2aReceiptVerifier.verify(facts, bytes, key) to key }.getOrNull()
        } ?: error("原回执签名或来源不可核验，请保留资料核对历史公钥")
        if (facts.request.operation != "OPEN") {
            val original = checkNotNull(db.localE2aReceipts().get(facts.stop.openOriginalKey))
            check(original.state == "VERIFIED")
            val open = JSONObject(checkNotNull(original.responseBytes).toString(Charsets.UTF_8))
            check(open.getJSONObject("originalContext").getString("scopeId") ==
                pair.first.getJSONObject("originalContext").getString("scopeId")) { "原回执来源范围不一致" }
        }
        return pair.second
    }
}
