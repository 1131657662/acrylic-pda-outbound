package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.data.PdaDatabase
import org.json.JSONArray
import java.util.UUID

/** Runs only inside the original freeze transaction, before any request can be sent. */
internal class LocalE2aTransferBuilder(private val db: PdaDatabase) {
    fun save(stop: LocalE2aStop, signer: (E2aStopProof.Facts) -> E2aStopProof.Signed) {
        check(db.inTransaction())
        val dao = db.localE2aTransfers()
        check(dao.get(stop.clientOrderId) == null && dao.requestCount(stop.clientOrderId) == 0L)
        val count = Math.addExact(Math.addExact(stop.captureHighWatermark, stop.scanSnapshotCount), stop.auditThroughSeq) + 1
        val pageCount = ((count + 127) / 128).also { check(it in 1..4096) }.toInt()
        val stopKey = UUID.randomUUID().toString()
        val verifyKey = UUID.randomUUID().toString()
        val digest = E2aRequestDigest(stopKey, verifyKey)
        saveRequest(E2aTransferRequests.saved(stop.clientOrderId, "OPEN", 0, stop.openOriginalKey,
            E2aTransferRequests.open(stop)), digest)
        val manifest = E2aWire.manifest(E2aTransferRequests.identity(stop), pageCount, pages(stop, pageCount, digest))
        val facts = E2aTransferRequests.facts(stop, manifest)
        val signed = signer(facts)
        E2aStopProof.verify(facts, signed.envelope, signed.publicKey)
        saveRequest(E2aTransferRequests.saved(stop.clientOrderId, "STOP", 0, stopKey,
            E2aTransferRequests.stop(stop, manifest, signed.envelope)), digest)
        saveRequest(E2aTransferRequests.saved(stop.clientOrderId, "VERIFY", 0, verifyKey,
            E2aTransferRequests.verify(stop, manifest, stopKey)), digest)
        dao.insert(LocalE2aTransfer(stop.clientOrderId, manifest.canonicalCore, manifest.bytesHash, manifest.setHash,
            stopKey, verifyKey, pageCount, count, digest.finish(), signed.publicKey))
    }

    private fun pages(stop: LocalE2aStop, pageCount: Int, digest: E2aRequestDigest): Sequence<E2aWire.Page> = sequence {
        var previous: String? = null
        var rawOrdinal = 0L
        E2aSnapshotReferences(db, stop).all().chunked(128).forEachIndexed { index, items ->
            items.forEach { item ->
                saveRequest(E2aTransferRequests.saved(stop.clientOrderId, "RAW",
                    ++rawOrdinal, item.getString("originalObjectId"), E2aTransferRequests.raw(stop, item)), digest)
            }
            val page = E2aWire.page(E2aTransferRequests.identity(stop), index + 1, pageCount, previous, JSONArray(items))
            saveRequest(E2aTransferRequests.saved(stop.clientOrderId, "PAGE",
                (index + 1).toLong(), UUID.randomUUID().toString(), E2aTransferRequests.page(stop, page)), digest)
            previous = page.hash
            yield(page)
        }
    }

    private fun saveRequest(row: LocalE2aRequest, digest: E2aRequestDigest) {
        db.localE2aTransfers().insertRequest(row)
        digest.append(row)
    }
}
