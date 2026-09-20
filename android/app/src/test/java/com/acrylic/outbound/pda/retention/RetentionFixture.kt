package com.acrylic.outbound.pda.retention

import com.acrylic.outbound.pda.data.*
import java.time.Duration
import java.time.Instant

internal class RetentionFixture(val db: PdaDatabase) {
    var now: Instant = Instant.parse("2026-01-01T00:00:00Z")
    var epoch = "1"
    val cleaner = RetentionCleaner(db, { epoch }, { now })

    init { db.sync().saveRecovery(LocalRecoveryControl(observedServerEpoch = epoch, mode = "NORMAL")) }
    fun state(stream: String, baseline: String = "old", warehouse: String = "warehouse", epoch: String = this.epoch) =
        LocalSyncState(epoch, warehouse, stream, cursor = "cursor-$baseline", activeBaselineId = baseline,
            lastSuccessAt = now.toString()).also(db.sync()::saveState)

    fun order(id: String = "order", baseline: String = "old", warehouse: String = "warehouse", epoch: String = this.epoch) =
        LocalCloudOrder(epoch, warehouse, baseline, id, "COMPLETED", 1, "2000-01-01T00:00:00Z").also(db.sync()::saveOrder)

    fun barcode(code: String = "00000000001", baseline: String = "old", warehouse: String = "warehouse", epoch: String = this.epoch) =
        LocalCloudBarcode(epoch, warehouse, baseline, code, "bale-$code", "DISPATCHED", 1, 1,
            "2000-01-01T00:00:00Z").also(db.barcodes()::save)

    fun retire() {
        state("ORDER"); state("BARCODE"); cleaner.clean()
        state("ORDER", "current"); state("BARCODE", "current"); cleaner.clean()
        now = now.plus(Duration.ofDays(90)).plusMillis(1)
    }
    fun has(row: LocalCloudOrder) = db.sync().order(row.serverRecoveryEpoch, row.warehouseId, row.baselineId, row.orderId) != null
    fun has(row: LocalCloudBarcode) = db.barcodes().get(row.serverRecoveryEpoch, row.warehouseId, row.baselineId, row.barcode) != null
}
