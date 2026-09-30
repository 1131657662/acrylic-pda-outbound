package com.acrylic.outbound.pda

import com.acrylic.outbound.pda.evidence.*
import com.acrylic.outbound.pda.network.ApiFailure
import com.acrylic.outbound.pda.temporary.TemporaryConnection
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URI

/** File-backed synthetic peer at the transport boundary; this is not an HTTP server. */
internal class R06A11DurablePeer(private val work: R06A11DeviceWork) {
    private val root = File(work.root, "peer").apply { mkdirs() }
    private val trace = File(root, "calls.txt")
    var drop: String? = null
    init { work.connection.receive = ::receive }

    fun prepare(issuer: R06A11ReceiptIssuer) {
        work.connection.session.put("signingKey", issuer.key)
        val counts = counts()
        for (request in work.requests()) {
            File(root, "${request.originalKey}.issued").writeBytes(
                issuer.response(LocalDraftReceiptVerifier.Expected(work.stop(), request, counts)))
        }
    }

    fun calls(verb: String): List<String> = if (!trace.exists()) emptyList() else trace.readLines()
        .filter { it.startsWith("$verb ") }.map { it.substringAfter(' ') }

    private fun receive(request: TemporaryConnection.Original): ByteArray {
        check(!work.db.inTransaction())
        if (request.body == null && request.raw == null) {
            val key = request.path.substringAfterLast('/')
            check(work.requests().any { it.originalKey == key })
            trace.appendText("GET $key\n")
            val saved = File(root, "$key.committed")
            if (!saved.isFile) throw ApiFailure("DRAFT_EVIDENCE_NOT_FOUND", "synthetic missing", httpStatus = 404)
            return saved.readBytes()
        }
        val key = URI(request.path).rawQuery.split('&').single { it.startsWith("originalKey=") }.substringAfter('=')
        val row = work.requests().single { it.originalKey == key }
        check(work.db.localDraftTransfers().receipt(key)?.state == "RESULT_UNKNOWN")
        verifyBody(request, row)
        check(key !in calls("POST")) { "Duplicate original-key POST: $key" }
        File(root, "$key.issued").copyTo(File(root, "$key.committed"))
        trace.appendText("POST $key\n")
        if (drop == row.operation) { drop = null; throw IOException("Dropped committed ${row.operation} receipt") }
        return File(root, "$key.committed").readBytes()
    }

    private fun verifyBody(input: TemporaryConnection.Original, row: LocalDraftRequest) {
        if (row.operation == "ITEM") {
            check(E2aWire.sha(checkNotNull(input.raw)) == JSONObject(row.requestJson).getString("bytesSha256"))
        } else check(row.requestJson.toByteArray().contentEquals(input.body))
    }

    private fun counts(): LocalDraftReceiptVerifier.Counts {
        val rows = work.db.localDraftStops().page(work.id, 0)
        val captures = rows.filter { it.kind == "CAPTURE" }
        val own = captures.count { JSONObject(it.rawBytes.toString(Charsets.UTF_8)).optString("clientOrderId") == work.id }
        return LocalDraftReceiptVerifier.Counts(own.toLong(), (captures.size - own).toLong(),
            rows.count { it.kind == "ARCHIVE" }.toLong())
    }
}
