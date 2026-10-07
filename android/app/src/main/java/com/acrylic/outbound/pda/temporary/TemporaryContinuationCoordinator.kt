package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.network.ApiFailure
import com.acrylic.outbound.pda.update.UpdateGate

/** Serializes durable user intent; retries always use the original preparation's saved ack key and bytes. */
class TemporaryContinuationCoordinator(private val store: TemporaryWorkStore) {
    @Synchronized fun queue(ticket: String) = UpdateGate.business {
        requireEnabled()
        store.db.runInTransaction {
            val link = checkNotNull(store.db.temporaryContinuations().get(ticket))
            store.load(link.newClientOrderId); val draft = store.check(link.newClientOrderId)
            check(draft.state in setOf("CONTINUATION_BLOCKED", "SCANNING"))
            val dao = store.db.temporaryContinuationQueue()
            dao.insert(TemporaryContinuationQueue(ticket))
            if (dao.get(ticket)!!.state != "READY") dao.progress(ticket, "PENDING", "", true)
        }
    }

    @Synchronized fun resume(ticket: String): TemporaryContinuationQueue = UpdateGate.business {
        requireEnabled()
        val dao = store.db.temporaryContinuationQueue(); val pending = checkNotNull(dao.get(ticket))
        val link = checkNotNull(store.db.temporaryContinuations().get(ticket))
        store.load(link.newClientOrderId); store.check(link.newClientOrderId, false)
        if (pending.state == "READY" || !pending.autoRetry) return@business pending
        try {
            TemporaryContinuationDownload(store).acknowledge(ticket)
            store.db.runInTransaction {
                TemporaryContinuationWork(store).start(link.newClientOrderId)
                check(dao.progress(ticket, "READY", "", false) == 1)
            }
        } catch (error: Exception) {
            val retry = error is java.io.IOException || error is ApiFailure &&
                error.code in setOf("INTERNAL_ERROR", "LOGIN_RATE_LIMIT", "SERVER_RECOVERY_REQUIRED")
            dao.progress(ticket, if (retry) "PENDING" else "ACTION_REQUIRED", error.message.orEmpty().take(500), retry)
            throw error
        }
        checkNotNull(dao.get(ticket))
    }

    private fun requireEnabled() {
        check(com.acrylic.outbound.pda.BuildConfig.DEBUG && store.continuationDevelopment) { "续作开发能力未启用，原记录保留" }
    }
}
