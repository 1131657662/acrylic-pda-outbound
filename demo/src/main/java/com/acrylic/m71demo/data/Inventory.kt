package com.acrylic.m71demo.data

import androidx.room.*

@Entity(tableName = "local_inventory_snapshot")
data class InventorySnapshot(
    @PrimaryKey val id: String,
    val sha256: String,
    val rowCount: Int,
    val source: String,
)

@Entity(tableName = "local_inventory_bale", primaryKeys = ["snapshotId", "barcode"],
    indices = [Index(value = ["snapshotId", "deliveryCode", "barcode"])],
    foreignKeys = [ForeignKey(entity = InventorySnapshot::class, parentColumns = ["id"],
        childColumns = ["snapshotId"], onDelete = ForeignKey.RESTRICT)])
data class InventoryBale(
    val snapshotId: String,
    val barcode: String,
    val deliveryCode: String,
    val model: String,
    val name: String,
    val weightGrams: Long,
    val rawWeight: String,
)

@Entity(tableName = "local_cloud_bale_state")
data class CloudBaleState(
    @PrimaryKey val barcode: String,
    val dispatchCycle: Long = 1,
    val recoveryEpoch: String = "DEMO_EPOCH_1",
    val state: String = "AVAILABLE",
)
