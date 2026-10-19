package com.acrylic.outbound.pda.independent

import androidx.room.*
import com.acrylic.outbound.pda.work.ScanTotal
import com.acrylic.outbound.pda.work.ScanProductTotal

@Dao
interface IndependentScanDao {
    @Insert fun insertCapture(row: IndependentCapture)
    @Insert fun insertState(row: IndependentCaptureState)
    @Update fun updateState(row: IndependentCaptureState)
    @Insert fun insertScan(row: IndependentScan)
    @Insert fun insertGuard(row: IndependentGuard)
    @Insert fun insertVoid(row: IndependentVoid)
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insertDevice(row: IndependentDeviceState)
    @Update fun updateDevice(row: IndependentDeviceState)
    @Query("SELECT * FROM independent_device_state WHERE deviceId=:id") fun device(id: String): IndependentDeviceState?
    @Query("SELECT c.*,s.* FROM independent_capture c INNER JOIN independent_capture_state s ON s.captureId=c.id WHERE c.id=:id")
    fun capture(id: String): IndependentCaptureView?
    @Query("""SELECT c.*,s.* FROM independent_capture c INNER JOIN independent_capture_state s ON s.captureId=c.id
        WHERE c.taskId=:id AND s.state IN ('RECEIVED','WAITING_CONFIRM','CONFIRM_REQUESTED') ORDER BY c.sequence LIMIT 1""")
    fun pending(id: String): IndependentCaptureView?
    @Query("""SELECT c.*,s.* FROM independent_capture c INNER JOIN independent_capture_state s ON s.captureId=c.id
        WHERE c.taskId=:id AND s.state IN ('INVALID','BUSINESS_REJECTED') AND s.failureAckAt IS NULL ORDER BY c.sequence LIMIT 1""")
    fun failure(id: String): IndependentCaptureView?
    @Query("""SELECT COUNT(*) FROM independent_capture c INNER JOIN independent_capture_state s ON s.captureId=c.id
        WHERE c.taskId!=:exceptId AND (s.state IN ('RECEIVED','WAITING_CONFIRM','CONFIRM_REQUESTED')
        OR (s.state IN ('INVALID','BUSINESS_REJECTED') AND s.failureAckAt IS NULL))""")
    fun unresolved(exceptId: String = ""): Int
    @Query("SELECT * FROM independent_guard WHERE barcode=:barcode") fun guard(barcode: String): IndependentGuard?
    @Query("DELETE FROM independent_guard WHERE captureId=:id") fun release(id: String): Int
    @Query("SELECT * FROM independent_scan WHERE captureId=:id") fun scan(id: String): IndependentScan?
    @Query("SELECT * FROM independent_void WHERE captureId=:id") fun voided(id: String): IndependentVoid?
    @Query("""SELECT s.*,CASE WHEN v.captureId IS NULL THEN 'ACTIVE' ELSE 'VOID' END AS status
        FROM independent_scan s LEFT JOIN independent_void v ON v.captureId=s.captureId
        WHERE s.taskId=:id AND s.sequence>:after ORDER BY s.sequence LIMIT 50""")
    fun page(id: String, after: Long): List<IndependentScanView>
    @Query("""SELECT s.*,CASE WHEN v.captureId IS NULL THEN 'ACTIVE' ELSE 'VOID' END AS status
        FROM independent_scan s LEFT JOIN independent_void v ON v.captureId=s.captureId
        WHERE s.taskId=:id AND s.barcode=:barcode ORDER BY s.sequence DESC LIMIT 1""")
    fun locate(id: String, barcode: String): IndependentScanView?
    @Query("""SELECT s.*,CASE WHEN v.captureId IS NULL THEN 'ACTIVE' ELSE 'VOID' END AS status
        FROM independent_capture c INNER JOIN independent_scan s ON s.captureId=c.id
        LEFT JOIN independent_void v ON v.captureId=s.captureId
        WHERE c.taskId=:id ORDER BY c.sequence DESC LIMIT 1""")
    fun latestSaved(id: String): IndependentScanView?
    @Query("""SELECT COUNT(*) AS count,COALESCE(SUM(s.weightGrams),0) AS grams FROM independent_scan s
        WHERE s.taskId=:id AND NOT EXISTS (SELECT 1 FROM independent_void v WHERE v.captureId=s.captureId)""")
    fun total(id: String): ScanTotal
    @Query("""SELECT s.deliveryCode,COUNT(*) AS count,COALESCE(SUM(s.weightGrams),0) AS grams FROM independent_scan s
        WHERE s.taskId=:id AND NOT EXISTS (SELECT 1 FROM independent_void v WHERE v.captureId=s.captureId) GROUP BY s.deliveryCode""")
    fun products(id: String): List<ScanProductTotal>
    @Query("SELECT c.*,s.* FROM independent_capture c INNER JOIN independent_capture_state s ON s.captureId=c.id WHERE c.taskId=:id ORDER BY c.sequence")
    fun captures(id: String): List<IndependentCaptureView>
    @Query("SELECT * FROM independent_void WHERE taskId=:id ORDER BY elapsed,captureId") fun voids(id: String): List<IndependentVoid>
    @Query("""SELECT COUNT(*) FROM independent_scan s WHERE s.taskId=:id AND NOT EXISTS (SELECT 1 FROM independent_void v WHERE v.captureId=s.captureId)
        AND NOT EXISTS (SELECT 1 FROM independent_guard g WHERE g.captureId=s.captureId AND g.taskId=s.taskId AND g.barcode=s.barcode)""")
    fun missingGuards(id: String): Int
    @Query("""SELECT COUNT(*) FROM independent_guard g WHERE g.taskId=:id AND (NOT EXISTS
        (SELECT 1 FROM independent_scan s WHERE s.captureId=g.captureId AND s.taskId=g.taskId AND s.barcode=g.barcode)
        OR EXISTS (SELECT 1 FROM independent_void v WHERE v.captureId=g.captureId))""")
    fun invalidGuards(id: String): Int
}
