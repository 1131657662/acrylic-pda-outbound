package com.acrylic.outbound.pda.data

import androidx.room.*

@Entity(tableName = "local_cloud_barcode", primaryKeys = ["serverRecoveryEpoch", "warehouseId", "baselineId", "barcode"],
    indices = [Index(value = ["serverRecoveryEpoch", "warehouseId", "baselineId", "baleId"], unique = true)])
data class LocalCloudBarcode(val serverRecoveryEpoch: String, val warehouseId: String, val baselineId: String,
    val barcode: String, val baleId: String, val status: String, val dispatchCycle: Long, val rowVersion: Long, val updatedAt: String)

@Dao
interface BarcodeDao {
    @Query("SELECT * FROM local_cloud_barcode WHERE serverRecoveryEpoch=:epoch AND warehouseId=:warehouse AND baselineId=:baseline AND barcode=:barcode")
    fun get(epoch: String, warehouse: String, baseline: String, barcode: String): LocalCloudBarcode?
    @Query("""SELECT b.* FROM local_cloud_barcode b INNER JOIN local_sync_state s
        ON b.serverRecoveryEpoch=s.serverRecoveryEpoch AND b.warehouseId=s.warehouseId AND b.baselineId=s.activeBaselineId
        WHERE b.serverRecoveryEpoch=:epoch AND b.warehouseId=:warehouse AND b.barcode=:barcode AND s.streamType='BARCODE'""")
    fun active(epoch: String, warehouse: String, barcode: String): LocalCloudBarcode?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun save(row: LocalCloudBarcode)
    @Query("DELETE FROM local_cloud_barcode WHERE serverRecoveryEpoch=:epoch AND warehouseId=:warehouse AND baselineId=:baseline")
    fun discardStaging(epoch: String, warehouse: String, baseline: String)
}
