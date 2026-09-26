package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.network.ApiFailure
import com.acrylic.outbound.pda.temporary.*
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.util.UUID

class E2aRecoveryFixture(val work: TemporaryWorkFixture) : TemporaryConnection by work.fixture {
    val signer = E2aTestReceipts()
    val received = linkedMapOf<String, ByteArray>()
    val posts = mutableListOf<LocalE2aRequest>()
    val reads = mutableListOf<String>()
    var drop: String? = null
    var auditComplete = true
    var auditCalls = 0
    var beforeResponse: () -> Unit = {}
    private val objects = mutableMapOf<String, JSONObject>()
    init { checkNotNull(work.fixture.auth).put("signingKey", signer.key) }

    fun repository() = LocalE2aRecoveryRepository({
        TemporaryWorkStore(work.db, this, work.store.sample)
    }) {
        check(!work.db.inTransaction()); auditCalls++
        if (auditComplete) {
            val stop = checkNotNull(work.db.localE2aStops().get(work.id))
            work.db.audit().save(com.acrylic.outbound.pda.data.AuditCheckpoint(stop.sourceRecoveryEpoch.toString(),
                stop.freezeDeviceId, stop.auditThroughSeq))
        }
    }

    override fun original(request: TemporaryConnection.Original): ByteArray {
        check(!work.db.inTransaction())
        if (request.body == null) {
            val key = request.path.substringAfter("receipts/").substringBefore('/')
            reads += key
            return received[key]?.copyOf() ?: throw ApiFailure("E2A_RECEIPT_NOT_FOUND", "不存在", httpStatus = 404)
        }
        val key = params(request.path).getValue("originalKey")
        val local = all().single { it.originalKey == key }
        check(local.requestJson.toByteArray().contentEquals(request.body))
        check(work.db.localE2aReceipts().get(key)?.state == "RESULT_UNKNOWN")
        val facts = E2aReceiptVerifier.Expected(checkNotNull(work.db.localE2aStops().get(work.id)), plan(), local)
        val bytes = received.getOrPut(key) { signer.receipt(facts) }
        posts += local; beforeResponse(); lose(local.operation)
        return bytes.copyOf()
    }

    override fun call(request: TemporaryConnection.Call): JSONObject {
        if (!request.path.contains("temporary-source-evidence/raw?")) return work.fixture.call(request)
        check(!work.db.inTransaction())
        val query = params(request.path); val key = query.getValue("originalObjectId")
        val local = all().single { it.originalKey == key }
        check(work.db.localE2aReceipts().get(key)?.state == "RESULT_UNKNOWN")
        check(E2aWire.sha(checkNotNull(request.raw)) == query.getValue("bytesSha256"))
        val result = objects.getOrPut(key) { JSONObject(local.requestJson).put("status", "STORED")
            .put("objectId", UUID.randomUUID().toString()).put("localDeletionAllowed", false) }
        posts += local; beforeResponse(); lose("RAW")
        return JSONObject(result.toString())
    }

    fun plan() = checkNotNull(work.db.localE2aTransfers().get(work.id))
    fun all() = E2aRequestSequence(work.db, plan()).all().toList()
    private fun lose(operation: String) { if (drop == operation) { drop = null; throw IOException("test lost response") } }
    private fun params(path: String) = URI(path).rawQuery.split('&').associate {
        val pair = it.split('=', limit = 2); pair[0] to URLDecoder.decode(pair[1], "UTF-8")
    }
}
