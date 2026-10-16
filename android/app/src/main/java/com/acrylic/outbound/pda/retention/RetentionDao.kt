package com.acrylic.outbound.pda.retention

import androidx.room.*
import com.acrylic.outbound.pda.data.LocalSyncState

@Dao
interface RetentionDao {
    @Query("SELECT * FROM local_sync_state WHERE serverRecoveryEpoch=:epoch AND streamType IN ('ORDER','BARCODE') ORDER BY warehouseId,streamType")
    fun states(epoch: String): List<LocalSyncState>
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun observe(row: RetainedBaseline)
    @Query("""UPDATE local_retained_baseline SET retiredAt=NULL WHERE serverRecoveryEpoch=:epoch
        AND warehouseId=:warehouse AND streamType=:stream AND baselineId=:active""")
    fun reactivate(epoch: String, warehouse: String, stream: String, active: String)
    @Query("""UPDATE local_retained_baseline SET retiredAt=:now WHERE serverRecoveryEpoch=:epoch
        AND warehouseId=:warehouse AND streamType=:stream AND baselineId!=:active
        AND retiredAt IS NULL AND successObservedAt<=:now""")
    fun retire(epoch: String, warehouse: String, stream: String, active: String, now: Long)
    @Query("""SELECT * FROM local_retained_baseline WHERE serverRecoveryEpoch=:epoch AND warehouseId=:warehouse
        AND streamType=:stream AND baselineId!=:active AND retiredAt<:cutoff
        AND successObservedAt<=retiredAt ORDER BY retiredAt,baselineId""")
    fun expired(epoch: String, warehouse: String, stream: String, active: String, cutoff: Long): List<RetainedBaseline>

    @Query("""DELETE FROM local_cloud_order WHERE rowid IN (
        SELECT c.rowid FROM local_cloud_order c WHERE c.serverRecoveryEpoch=:epoch
        AND c.warehouseId=:warehouse AND c.baselineId=:baseline
        AND NOT EXISTS (SELECT 1 FROM prepared_order p WHERE p.orderId=c.orderId)
        AND NOT EXISTS (SELECT 1 FROM work_order w WHERE w.orderId=c.orderId)
        AND NOT EXISTS (SELECT 1 FROM claim_intent i WHERE i.orderId=c.orderId)
        ORDER BY c.orderId LIMIT :limit)""")
    fun deleteOrders(epoch: String, warehouse: String, baseline: String, limit: Int): Int

    // Every source barcode is NOT NULL. UNION materializes the protected set once, avoiding a
    // full prepared_bale/work_capture scan for every old projection row on a low-end PDA.
    @Query("""DELETE FROM local_cloud_barcode WHERE rowid IN (
        SELECT c.rowid FROM local_cloud_barcode c WHERE c.serverRecoveryEpoch=:epoch
        AND c.warehouseId=:warehouse AND c.baselineId=:baseline
        AND c.barcode NOT IN (SELECT barcode FROM prepared_bale UNION SELECT barcode FROM work_capture
            UNION SELECT barcode FROM work_guard UNION SELECT barcode FROM work_scan
            UNION SELECT barcode FROM temporary_capture UNION SELECT barcode FROM temporary_guard UNION SELECT barcode FROM temporary_scan
            UNION SELECT barcode FROM independent_capture UNION SELECT barcode FROM independent_guard UNION SELECT barcode FROM independent_scan)
        ORDER BY c.barcode LIMIT :limit)""")
    fun deleteBarcodes(epoch: String, warehouse: String, baseline: String, limit: Int): Int
}
