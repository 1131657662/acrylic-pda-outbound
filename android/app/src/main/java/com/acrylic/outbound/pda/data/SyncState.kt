package com.acrylic.outbound.pda.data

import androidx.room.*

@Entity(tableName = "local_sync_state", primaryKeys = ["serverRecoveryEpoch", "warehouseId", "streamType"])
data class LocalSyncState(
    val serverRecoveryEpoch: String, val warehouseId: String, val streamType: String = "ORDER",
    val cursor: String? = null, val sequence: Long = 0, val activeBaselineId: String? = null,
    val downloadId: String? = null, val downloadCursor: String? = null, val downloadWatermark: Long? = null,
    val lastSuccessAt: String? = null
)

@Entity(tableName = "local_cloud_order", primaryKeys = ["serverRecoveryEpoch", "warehouseId", "baselineId", "orderId"])
data class LocalCloudOrder(
    val serverRecoveryEpoch: String, val warehouseId: String, val baselineId: String, val orderId: String,
    val status: String, val rowVersion: Long, val updatedAt: String
)

@Entity(tableName = "local_recovery_control")
data class LocalRecoveryControl(
    @PrimaryKey val singletonId: Int = 1, val observedServerEpoch: String, val mode: String,
    val recoverySessionId: String? = null
)

@Dao
abstract class SyncDao {
    @Query("SELECT * FROM local_sync_state WHERE serverRecoveryEpoch=:epoch AND warehouseId=:warehouse AND streamType='ORDER'")
    abstract fun state(epoch: String, warehouse: String): LocalSyncState?
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract fun saveState(state: LocalSyncState)
    @Query("SELECT * FROM local_cloud_order WHERE serverRecoveryEpoch=:epoch AND warehouseId=:warehouse AND baselineId=:baseline AND orderId=:order")
    abstract fun order(epoch: String, warehouse: String, baseline: String, order: String): LocalCloudOrder?
    @Query("""SELECT o.* FROM local_cloud_order o INNER JOIN local_sync_state s
        ON o.serverRecoveryEpoch=s.serverRecoveryEpoch AND o.warehouseId=s.warehouseId AND o.baselineId=s.activeBaselineId
        WHERE o.serverRecoveryEpoch=:epoch AND o.orderId=:order AND s.streamType='ORDER'""")
    abstract fun activeOrder(epoch: String, order: String): LocalCloudOrder?
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract fun saveOrder(order: LocalCloudOrder)
    @Query("DELETE FROM local_cloud_order WHERE serverRecoveryEpoch=:epoch AND warehouseId=:warehouse AND baselineId=:baseline")
    abstract fun discardStaging(epoch: String, warehouse: String, baseline: String)
    @Query("SELECT * FROM local_recovery_control WHERE singletonId=1") abstract fun recovery(): LocalRecoveryControl?
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract fun saveRecovery(control: LocalRecoveryControl)
}
