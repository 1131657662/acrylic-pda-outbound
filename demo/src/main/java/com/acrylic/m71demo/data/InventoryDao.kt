package com.acrylic.m71demo.data

import androidx.room.*

@Dao
interface InventoryDao {
    @Insert fun insertSnapshot(snapshot: InventorySnapshot)
    @Insert fun insertBales(bales: List<InventoryBale>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insertStates(states: List<CloudBaleState>)
    @Query("SELECT * FROM local_inventory_snapshot WHERE id=:id")
    fun snapshot(id: String): InventorySnapshot?
    @Query("SELECT * FROM local_inventory_bale WHERE snapshotId=:snapshot AND barcode=:barcode")
    fun bale(snapshot: String, barcode: String): InventoryBale?
    @Query("SELECT * FROM local_cloud_bale_state WHERE barcode=:barcode")
    fun state(barcode: String): CloudBaleState?
    @Query("SELECT * FROM local_inventory_bale WHERE snapshotId=:snapshot AND barcode>:after ORDER BY barcode LIMIT :limit")
    fun page(snapshot: String, after: String, limit: Int): List<InventoryBale>
    @Query("UPDATE local_cloud_bale_state SET dispatchCycle=dispatchCycle+1 WHERE barcode=:barcode")
    fun simulateCycleChange(barcode: String)
}
