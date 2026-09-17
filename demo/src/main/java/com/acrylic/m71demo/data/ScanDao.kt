package com.acrylic.m71demo.data

import androidx.room.*

@Dao
interface ScanDao {
    @Insert fun insert(scan: ScanRecord)
    @Insert fun insertBatch(scans: List<ScanRecord>)
    @Insert fun insertGuard(guard: BarcodeGuard)
    @Insert fun insertGuards(guards: List<BarcodeGuard>)
    @Update fun update(scan: ScanRecord)
    @Query("SELECT * FROM local_scan WHERE id=:id") fun get(id: String): ScanRecord?
    @Query("SELECT * FROM local_barcode_guard WHERE barcode=:barcode") fun guard(barcode: String): BarcodeGuard?
    @Query("DELETE FROM local_barcode_guard WHERE scanId=:id AND state='SCANNED'") fun release(id: String)
    @Query("SELECT * FROM local_scan WHERE orderId=:id AND orderScanSeq>:after ORDER BY orderScanSeq LIMIT :limit")
    fun page(id: String, after: Long, limit: Int): List<ScanRecord>
    @Query("SELECT * FROM local_scan WHERE orderId=:id ORDER BY orderScanSeq DESC LIMIT 5")
    fun recent(id: String): List<ScanRecord>
    @Query("SELECT * FROM local_scan WHERE orderId=:id AND status='ACTIVE' ORDER BY orderScanSeq DESC LIMIT 1")
    fun lastActive(id: String): ScanRecord?
    @Query("SELECT COUNT(*) FROM local_scan WHERE orderId=:id AND status='ACTIVE'") fun count(id: String): Int
}
