package com.acrylic.outbound.pda.retention

import com.acrylic.outbound.pda.data.*
import com.acrylic.outbound.pda.update.UpdateGate
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Callable

data class RetentionResult(val orders: Int = 0, val barcodes: Int = 0, val deferred: Boolean = false)

/** Only disposable cloud projections; no business evidence, inventory packages or counters. */
class RetentionCleaner(private val db: PdaDatabase, private val epoch: () -> String, private val now: () -> Instant) {
    fun clean(): RetentionResult = UpdateGate.maintenance {
        val currentEpoch = epoch()
        val time = now().toEpochMilli()
        db.runInTransaction(Callable { sweep(currentEpoch, time) })
    } ?: RetentionResult(deferred = true)

    private fun sweep(epoch: String, time: Long): RetentionResult {
        val recovery = db.sync().recovery()
        if (recovery?.mode != "NORMAL" || recovery.observedServerEpoch != epoch || time < RETENTION_MS)
            return RetentionResult(deferred = true)
        var result = RetentionResult()
        for (state in db.retention().states(epoch).filter(::complete)) {
            observe(state, time)
            val expired = db.retention().expired(epoch, state.warehouseId, state.streamType,
                checkNotNull(state.activeBaselineId), time - RETENTION_MS)
            for (baseline in expired) result = delete(baseline, result)
        }
        return result
    }

    private fun complete(state: LocalSyncState): Boolean = !state.activeBaselineId.isNullOrBlank() &&
        !state.cursor.isNullOrBlank() && state.downloadId == null && state.downloadCursor == null &&
        state.downloadWatermark == null && state.lastSuccessAt?.let { runCatching { Instant.parse(it) }.isSuccess } == true

    private fun observe(state: LocalSyncState, time: Long) {
        val active = checkNotNull(state.activeBaselineId)
        val dao = db.retention()
        dao.observe(RetainedBaseline(state.serverRecoveryEpoch, state.warehouseId, state.streamType, active, time))
        dao.reactivate(state.serverRecoveryEpoch, state.warehouseId, state.streamType, active)
        dao.retire(state.serverRecoveryEpoch, state.warehouseId, state.streamType, active, time)
    }

    private fun delete(row: RetainedBaseline, result: RetentionResult): RetentionResult = when (row.streamType) {
        "ORDER" -> if (result.orders >= BATCH_SIZE) result else result.copy(orders = result.orders +
            db.retention().deleteOrders(row.serverRecoveryEpoch, row.warehouseId, row.baselineId, BATCH_SIZE - result.orders))
        "BARCODE" -> if (result.barcodes >= BATCH_SIZE) result else result.copy(barcodes = result.barcodes +
            db.retention().deleteBarcodes(row.serverRecoveryEpoch, row.warehouseId, row.baselineId, BATCH_SIZE - result.barcodes))
        else -> result
    }

    private companion object {
        val RETENTION_MS = Duration.ofDays(90).toMillis()
        const val BATCH_SIZE = 500
    }
}
