package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.network.ApiFailure
import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.temporary.TemporaryConnection
import org.json.JSONObject
import java.net.URLEncoder

internal class E2aTransport(private val db: PdaDatabase, private val connection: TemporaryConnection,
    private val expected: JSONObject) {
    private val base = "pda/temporary-source-evidence/"

    fun receive(facts: E2aReceiptVerifier.Expected): ByteArray {
        val request = facts.request
        if (request.operation == "RAW") return raw(request)
        val query = query(JSONObject().put("warehouseId", facts.stop.warehouseId)
            .put("submissionId", facts.stop.submissionId).put("workRound", 1))
        val original = try {
            connection.original(TemporaryConnection.Original(base + "receipts/${request.originalKey}/original?" + query, expected))
        } catch (error: ApiFailure) {
            if (error.httpStatus == 404 && error.code == "E2A_RECEIPT_NOT_FOUND") null else throw error
        }
        if (original != null) return original
        val keys = "&originalKey=${request.originalKey}&openOriginalKey=${facts.stop.openOriginalKey}" +
            "&stopOriginalKey=${facts.plan.stopOriginalKey}"
        return connection.original(TemporaryConnection.Original(
            base + request.operation.lowercase(java.util.Locale.ROOT) + "?" + query + keys, expected,
            request.requestJson.toByteArray(Charsets.UTF_8)))
    }

    private fun raw(request: LocalE2aRequest): ByteArray {
        val metadata = JSONObject(request.requestJson)
        val objectRow = checkNotNull(db.localE2aObjects().byObject(request.clientOrderId, request.originalKey))
        check(objectRow.rawBytes.size.toLong() == metadata.getLong("byteCount") &&
            E2aWire.sha(objectRow.rawBytes) == metadata.getString("bytesSha256")) { "原始材料字节变化，停止发送" }
        val data = connection.call(TemporaryConnection.Call(base + "raw?" + query(metadata), expected, raw = objectRow.rawBytes))
        E2aRawAcknowledgement.verify(request, data)
        return SubmissionJson.canonical(data).toByteArray(Charsets.UTF_8)
    }

    private fun query(fields: JSONObject): String = fields.keys().asSequence().sorted().joinToString("&") {
        it + "=" + URLEncoder.encode(fields.get(it).toString(), "UTF-8")
    }
}
