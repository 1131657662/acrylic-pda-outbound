package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.data.PdaDatabase

internal class E2aRequestSequence(private val db: PdaDatabase, private val plan: LocalE2aTransfer) {
    fun all(): Sequence<LocalE2aRequest> = sequence {
        yield(required("OPEN", 0))
        var raw = 0L
        for (page in 1..plan.pageCount) {
            val end = minOf(page * 128L, plan.rawCount)
            while (raw < end) yield(required("RAW", ++raw))
            yield(required("PAGE", page.toLong()))
        }
        check(raw == plan.rawCount)
        yield(required("STOP", 0)); yield(required("VERIFY", 0))
    }
    private fun required(operation: String, ordinal: Long) =
        checkNotNull(db.localE2aTransfers().request(plan.clientOrderId, operation, ordinal))
}
