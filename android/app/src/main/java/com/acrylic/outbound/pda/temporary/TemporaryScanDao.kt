package com.acrylic.outbound.pda.temporary

import androidx.room.*
import com.acrylic.outbound.pda.work.ScanTotal
import com.acrylic.outbound.pda.work.ScanProductTotal

@Dao
interface TemporaryScanDao {
    @Insert fun insertCapture(row: TemporaryCapture)
    @Update fun updateCapture(row: TemporaryCapture)
    @Query("SELECT * FROM temporary_capture WHERE id=:id") fun capture(id: String): TemporaryCapture?
    @Query("SELECT * FROM temporary_capture WHERE clientOrderId=:id AND state IN ('RECEIVED','WAITING_CONFIRM','CONFIRM_REQUESTED') ORDER BY orderScanSeq LIMIT 1")
    fun pending(id: String): TemporaryCapture?
    @Query("SELECT * FROM temporary_capture WHERE clientOrderId=:id AND state IN ('INVALID','BUSINESS_REJECTED') AND failureAckAt IS NULL ORDER BY orderScanSeq LIMIT 1")
    fun failure(id: String): TemporaryCapture?
    @Query("""SELECT COUNT(*) FROM temporary_capture WHERE clientOrderId!=:exceptId AND
        (state IN ('RECEIVED','WAITING_CONFIRM','CONFIRM_REQUESTED') OR (state IN ('INVALID','BUSINESS_REJECTED') AND failureAckAt IS NULL))""")
    fun unresolved(exceptId: String = ""): Int
    @Insert fun insertScan(row: TemporaryScan)
    @Query("SELECT * FROM temporary_scan WHERE captureId=:id") fun scan(id: String): TemporaryScan?
    @Query("SELECT * FROM temporary_scan WHERE clientOrderId=:id ORDER BY orderScanSeq DESC LIMIT 5") fun recent(id: String): List<TemporaryScan>
    @Query("SELECT * FROM temporary_scan WHERE clientOrderId=:id AND orderScanSeq>:after ORDER BY orderScanSeq LIMIT 50")
    fun page(id: String, after: Long): List<TemporaryScan>
    @Query("SELECT * FROM temporary_scan WHERE clientOrderId=:id AND status='ACTIVE' ORDER BY orderScanSeq DESC LIMIT 1")
    fun last(id: String): TemporaryScan?
    @Query("UPDATE temporary_scan SET status='VOID' WHERE captureId=:id AND status='ACTIVE'") fun markVoid(id: String): Int
    @Query("SELECT COUNT(*) AS count,COALESCE(SUM(weightGrams),0) AS grams FROM temporary_scan WHERE clientOrderId=:id AND status='ACTIVE'")
    fun total(id: String): ScanTotal
    @Query("SELECT deliveryCode,COUNT(*) AS count,COALESCE(SUM(weightGrams),0) AS grams FROM temporary_scan WHERE clientOrderId=:id AND status='ACTIVE' GROUP BY deliveryCode")
    fun products(id: String): List<ScanProductTotal>
    @Insert fun insertGuard(row: TemporaryGuard)
    @Query("SELECT * FROM temporary_guard WHERE barcode=:barcode") fun guard(barcode: String): TemporaryGuard?
    @Query("DELETE FROM temporary_guard WHERE captureId=:id") fun release(id: String): Int
    @Insert fun insertEvent(row: TemporaryEvent)
    @Query("SELECT * FROM temporary_event WHERE clientOrderId=:id ORDER BY localSeq") fun events(id: String): List<TemporaryEvent>
    @Query("SELECT MIN(localSeq) FROM temporary_event WHERE originDeviceId=:device AND localSeq>:after")
    fun nextAudit(device: String, after: Long): Long?
}
