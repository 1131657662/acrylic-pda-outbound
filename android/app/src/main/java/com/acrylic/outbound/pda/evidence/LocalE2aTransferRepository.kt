package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.temporary.TemporaryWorkStore
import com.acrylic.outbound.pda.update.UpdateGate
import org.json.JSONArray
import java.util.concurrent.Callable

class LocalE2aTransferRepository(private val store: TemporaryWorkStore) {
    fun record(id: String): LocalE2aTransfer = UpdateGate.business {
        LocalE2aStopRepository(store).record(id)
        store.db.runInTransaction(Callable {
            store.check(id, false)
            val stop = checkNotNull(store.db.localE2aStops().get(id))
            val frozen = checkNotNull(store.db.temporarySubmissions().forOrder(id))
            LocalE2aSnapshot(store.db).verify(stop, frozen)
            verify(store.db, stop)
        })
    }

    private fun verify(db: PdaDatabase, stop: LocalE2aStop): LocalE2aTransfer {
        val plan = checkNotNull(db.localE2aTransfers().get(stop.clientOrderId)) {
            "缺少原冻结请求，不可补造原键，请保留资料核对"
        }
        val keys = hashSetOf(plan.stopOriginalKey, plan.verifyOriginalKey)
        check(keys.size == 2)
        keys.forEach(E2aItemValidation::uuid)
        val digest = E2aRequestDigest(plan.stopOriginalKey, plan.verifyOriginalKey)
        val open = request(db, stop, "OPEN", 0, keys)
        check(open.originalKey == stop.openOriginalKey)
        E2aTransferRequests.requireBody(open, E2aTransferRequests.open(stop))
        digest.append(open)
        val manifest = E2aWire.manifest(E2aTransferRequests.identity(stop), plan.pageCount,
            pages(db, stop, plan, keys, digest))
        verifyStop(db, stop, plan, manifest, digest)
        check(plan.manifestJson == manifest.canonicalCore && plan.manifestBytesHash == manifest.bytesHash &&
            plan.setHash == manifest.setHash && plan.requestSetSha256 == digest.finish())
        check(db.localE2aTransfers().requestCount(stop.clientOrderId) == 3 + plan.rawCount + plan.pageCount)
        return plan
    }

    private fun verifyStop(db: PdaDatabase, stop: LocalE2aStop, plan: LocalE2aTransfer,
        manifest: E2aWire.Manifest, digest: E2aRequestDigest) {
        val row = checkNotNull(db.localE2aTransfers().request(stop.clientOrderId, "STOP", 0))
        val proof = org.json.JSONObject(row.requestJson).getJSONObject("stopProof")
        check(row.originalKey == plan.stopOriginalKey)
        E2aStopProof.verify(E2aTransferRequests.facts(stop, manifest), proof, plan.devicePublicKey)
        E2aTransferRequests.requireBody(row, E2aTransferRequests.stop(stop, manifest, proof))
        digest.append(row)
        val verify = checkNotNull(db.localE2aTransfers().request(stop.clientOrderId, "VERIFY", 0))
        check(verify.originalKey == plan.verifyOriginalKey)
        E2aTransferRequests.requireBody(verify, E2aTransferRequests.verify(stop, manifest, plan.stopOriginalKey))
        digest.append(verify)
    }

    private fun pages(db: PdaDatabase, stop: LocalE2aStop, plan: LocalE2aTransfer,
        keys: MutableSet<String>, digest: E2aRequestDigest): Sequence<E2aWire.Page> = sequence {
        var previous: String? = null
        var ordinal = 0L
        E2aSnapshotReferences(db, stop).all().chunked(128).forEachIndexed { index, items ->
            items.forEach { item ->
                val raw = request(db, stop, "RAW", ++ordinal, keys)
                check(raw.originalKey == item.getString("originalObjectId"))
                E2aTransferRequests.requireBody(raw, E2aTransferRequests.raw(stop, item))
                digest.append(raw)
            }
            val page = E2aWire.page(E2aTransferRequests.identity(stop), index + 1, plan.pageCount, previous, JSONArray(items))
            val saved = request(db, stop, "PAGE", (index + 1).toLong(), keys)
            E2aTransferRequests.requireBody(saved, E2aTransferRequests.page(stop, page))
            digest.append(saved)
            previous = page.hash
            yield(page)
        }
        check(ordinal == plan.rawCount)
    }

    private fun request(db: PdaDatabase, stop: LocalE2aStop, operation: String, ordinal: Long,
        keys: MutableSet<String>): LocalE2aRequest {
        val row = checkNotNull(db.localE2aTransfers().request(stop.clientOrderId, operation, ordinal))
        check(keys.add(row.originalKey))
        return row
    }
}
