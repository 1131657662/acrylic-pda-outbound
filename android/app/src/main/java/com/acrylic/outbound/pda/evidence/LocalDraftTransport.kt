package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.network.ApiFailure
import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.temporary.TemporaryConnection
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

internal class LocalDraftTransport(private val db: PdaDatabase, private val connection: TemporaryConnection,
    private val expected: JSONObject, private val stop: LocalDraftStop) {
    fun receive(request: LocalDraftRequest): ByteArray {
        check(!db.inTransaction())
        val base = "pda/local-draft-evidence/${stop.evidenceId}"
        val saved = try { connection.original(TemporaryConnection.Original("$base/receipts/${request.originalKey}", expected)) }
        catch (error: ApiFailure) {
            if (error.httpStatus == 404 && error.code == "DRAFT_EVIDENCE_NOT_FOUND") null else throw error
        }
        if (saved != null) return saved
        val path = "$base/${request.operation.lowercase(Locale.ROOT)}?originalKey=${request.originalKey}"
        if (request.operation != "ITEM") return connection.original(TemporaryConnection.Original(
            path, expected, request.requestJson.toByteArray(Charsets.UTF_8)))
        val row = checkNotNull(db.localDraftStops().page(stop.clientOrderId, request.position - 1).firstOrNull())
        check(row.ordinal == request.position && E2aWire.sha(row.rawBytes) == row.bytesSha256)
        val descriptor = LocalDraftRequests.descriptor(stop, row)
        check(SubmissionJson.canonical(descriptor) == request.requestJson)
        val query = descriptor.keys().asSequence().filter { it != "evidenceId" }.sorted().joinToString("&") {
            it + "=" + URLEncoder.encode(descriptor.get(it).toString(), "UTF-8")
        }
        return connection.original(TemporaryConnection.Original("$path&$query", expected, raw = row.rawBytes))
    }
}
