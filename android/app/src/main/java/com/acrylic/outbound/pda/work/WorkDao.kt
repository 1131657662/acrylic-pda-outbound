package com.acrylic.outbound.pda.work

import androidx.room.*

@Dao
interface WorkDao {
    @Query("SELECT * FROM work_order WHERE assignmentId=:id") fun order(id: String): WorkOrder?
    @Query("SELECT state FROM work_order WHERE assignmentId=:id") fun state(id: String): String?
    @Query("SELECT * FROM work_line WHERE assignmentId=:id ORDER BY deliveryCode") fun lines(id: String): List<WorkLine>
    @Query("SELECT * FROM work_line WHERE assignmentId=:id AND deliveryCode=:code") fun line(id: String, code: String): WorkLine?
    @Query("SELECT * FROM work_device WHERE deviceId=:id") fun device(id: String): WorkDevice?
    @Insert fun insertOrder(row: WorkOrder)
    @Insert fun insertLines(rows: List<WorkLine>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insertDevice(row: WorkDevice)
    @Update fun updateDevice(row: WorkDevice)
    @Query("UPDATE work_order SET nextOrderScanSeq=nextOrderScanSeq+1 WHERE assignmentId=:id AND nextOrderScanSeq=:expected")
    fun advanceOrder(id: String, expected: Long): Int
    @Query("UPDATE work_order SET actualCount=:count,weightGrams=:grams,startedAt=COALESCE(startedAt,:started) WHERE assignmentId=:id")
    fun totals(id: String, count: Long, grams: Long, started: String?): Int
    @Query("UPDATE work_line SET actualCount=:count,weightGrams=:grams WHERE assignmentId=:id AND deliveryCode=:code")
    fun lineTotals(id: String, code: String, count: Long, grams: Long): Int
    @Query("UPDATE work_order SET state='SCANNING' WHERE assignmentId=:id AND state='RECALLED'") fun continueWork(id: String): Int
    @Query("UPDATE work_order SET state='SCANNING' WHERE assignmentId=:id AND state='HANDOVER_FROZEN'") fun continueHandover(id: String): Int
    @Insert fun insertEvent(row: WorkEvent)
    @Query("UPDATE work_order SET state='FROZEN' WHERE assignmentId=:id AND state='SCANNING'") fun freeze(id: String): Int
    @Query("SELECT * FROM work_event WHERE assignmentId=:id ORDER BY orderPosition,localSeq") fun events(id: String): List<WorkEvent>
    @Query("SELECT COALESCE(MAX(orderPosition),0) FROM work_event WHERE assignmentId=:id") fun eventPosition(id: String): Long
    @Query("SELECT * FROM work_guard WHERE assignmentId=:id ORDER BY barcode") fun guards(id: String): List<WorkGuard>
    @Query("SELECT * FROM work_event WHERE originDeviceId=:device ORDER BY localSeq") fun deviceEvents(device: String): List<WorkEvent>
    @Query("SELECT COUNT(*) FROM work_capture WHERE state IN ('RECEIVED','WAITING_CONFIRM','CONFIRM_REQUESTED') OR (state IN ('INVALID','BUSINESS_REJECTED') AND failureAckAt IS NULL)")
    fun unresolvedCount(): Int
    @Query("UPDATE work_order SET state='RECALL_PENDING' WHERE assignmentId=:id AND state IN ('SCANNING','FROZEN')") fun recall(id: String): Int
    @Query("UPDATE work_order SET state='RECALLED' WHERE assignmentId=:id AND state='RECALL_PENDING'") fun recalled(id: String): Int
}
