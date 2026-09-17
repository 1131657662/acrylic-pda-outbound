package com.acrylic.outbound.pda.work

import androidx.room.*

@Dao
interface WorkDao {
    @Query("SELECT * FROM work_order WHERE assignmentId=:id") fun order(id: String): WorkOrder?
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
    @Insert fun insertEvent(row: WorkEvent)
    @Query("UPDATE work_order SET state='FROZEN' WHERE assignmentId=:id AND state='SCANNING'") fun freeze(id: String): Int
    @Query("SELECT * FROM work_event WHERE assignmentId=:id ORDER BY localSeq") fun events(id: String): List<WorkEvent>
}
