package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.temporary.TemporaryWorkStore
import org.json.JSONObject

internal class LocalDraftStopSignatures(private val store: TemporaryWorkStore) {
    fun create(stop: LocalDraftStop) {
        check(store.db.inTransaction() && store.db.localDraftStopSignatures().get(stop.clientOrderId) == null)
        val key = store.connection.localDraftStopKey()
        val proof = store.connection.signLocalDraftStop(stop)
        check(proof.publicKey == key && store.connection.localDraftStopKey() == key)
        LocalDraftStopProof.verify(stop, proof.envelope, key)
        store.db.localDraftStopSignatures().insert(LocalDraftStopSignature(stop.clientOrderId, stop.evidenceId,
            key, SubmissionJson.canonical(proof.envelope)))
        require(stop)
    }

    fun require(stop: LocalDraftStop): LocalDraftStopSignature {
        check(store.db.inTransaction())
        val row = checkNotNull(store.db.localDraftStopSignatures().get(stop.clientOrderId)) {
            "原停止没有同事务设备签名，保留原件，不得补签"
        }
        check(row.evidenceId == stop.evidenceId && row.publicKey == store.connection.localDraftStopKey())
        val envelope = JSONObject(row.proofJson)
        check(SubmissionJson.canonical(envelope) == row.proofJson)
        LocalDraftStopProof.verify(stop, envelope, row.publicKey)
        return row
    }
}
