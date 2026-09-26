package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.network.ApiFailure
import com.acrylic.outbound.pda.temporary.*
import org.json.JSONObject
import java.io.IOException
import java.net.URI

class LocalDraftRecoveryFixture(val work: TemporaryWorkFixture) : TemporaryConnection by work.fixture {
    val signer = LocalDraftTestReceipts()
    val stored = linkedMapOf<String, ByteArray>()
    val posts = mutableListOf<LocalDraftRequest>()
    val reads = mutableListOf<String>()
    var drop: String? = null
    var beforeResponse: () -> Unit = {}
    var failReads: ApiFailure? = null
    init { checkNotNull(work.fixture.auth).put("signingKey", signer.key) }
    fun repository() = LocalDraftRecoveryRepository { TemporaryWorkStore(work.db, this, work.store.sample) }
    fun all() = work.db.localDraftTransfers().page(work.id, -1)
    fun stop() = checkNotNull(work.db.localDraftStops().get(work.id))
    override fun original(request: TemporaryConnection.Original): ByteArray {
        check(!work.db.inTransaction())
        if (request.body == null && request.raw == null) {
            val key = request.path.substringAfterLast('/'); reads += key
            failReads?.let { throw it }
            return stored[key]?.copyOf() ?: throw ApiFailure("DRAFT_EVIDENCE_NOT_FOUND", "missing", httpStatus = 404)
        }
        val key = URI(request.path).rawQuery.split('&').single { it.startsWith("originalKey=") }.substringAfter('=')
        val row = all().single { it.originalKey == key }
        check(work.db.localDraftTransfers().receipt(key)?.state == "RESULT_UNKNOWN")
        if (row.operation == "ITEM") check(E2aWire.sha(checkNotNull(request.raw)) == JSONObject(row.requestJson).getString("bytesSha256"))
        else check(row.requestJson.toByteArray().contentEquals(request.body))
        val facts = LocalDraftReceiptVerifier.Expected(stop(), row, counts())
        val response = stored.getOrPut(key) { signer.response(facts) }; posts += row; beforeResponse()
        if (drop == row.operation) { drop = null; throw IOException("Injected dropped ${row.operation}") }
        return response.copyOf()
    }
    fun counts(): LocalDraftReceiptVerifier.Counts {
        val rows = work.db.localDraftStops().page(work.id, 0)
        val captures = rows.filter { it.kind == "CAPTURE" }
        val own = captures.count { JSONObject(it.rawBytes.toString(Charsets.UTF_8)).optString("clientOrderId") == work.id }
        return LocalDraftReceiptVerifier.Counts(own.toLong(), (captures.size - own).toLong(), rows.count { it.kind == "ARCHIVE" }.toLong())
    }
}
