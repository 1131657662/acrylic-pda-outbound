package com.acrylic.outbound.pda.work

import androidx.room.*

@Dao
interface ScanDao {
    @Insert fun insert(row: WorkScan)
    @Insert fun insertGuard(row: WorkGuard)
    @Query("SELECT * FROM work_guard WHERE barcode=:barcode") fun guard(barcode: String): WorkGuard?
    @Query("SELECT * FROM work_scan WHERE captureId=:id") fun get(id: String): WorkScan?
    @Query("SELECT * FROM work_scan WHERE assignmentId=:id ORDER BY orderScanSeq DESC LIMIT 5") fun recent(id: String): List<WorkScan>
    @Query("SELECT * FROM work_scan s WHERE assignmentId=:id AND status='ACTIVE' AND NOT EXISTS (SELECT 1 FROM local_disposition d WHERE d.captureId=s.captureId) ORDER BY orderScanSeq DESC LIMIT 1") fun lastActive(id: String): WorkScan?
    @Query("SELECT * FROM work_scan WHERE assignmentId=:id AND orderScanSeq>:after ORDER BY orderScanSeq LIMIT :limit")
    fun page(id: String, after: Long, limit: Int): List<WorkScan>
    @Query("UPDATE work_scan SET status='VOID' WHERE captureId=:id AND status='ACTIVE'") fun markVoid(id: String): Int
    @Query("DELETE FROM work_guard WHERE captureId=:id") fun release(id: String): Int
    @Query("SELECT COUNT(*) AS count,COALESCE(SUM(weightGrams),0) AS grams FROM work_scan s WHERE assignmentId=:id AND status='ACTIVE' AND NOT EXISTS (SELECT 1 FROM local_disposition d WHERE d.captureId=s.captureId)")
    fun total(id: String): ScanTotal
    @Query("SELECT deliveryCode,COUNT(*) AS count,COALESCE(SUM(weightGrams),0) AS grams FROM work_scan s WHERE assignmentId=:id AND status='ACTIVE' AND NOT EXISTS (SELECT 1 FROM local_disposition d WHERE d.captureId=s.captureId) GROUP BY deliveryCode")
    fun products(id: String): List<ScanProductTotal>
}
data class ScanTotal(val count: Long, val grams: Long)
data class ScanProductTotal(val deliveryCode: String, val count: Long, val grams: Long)
