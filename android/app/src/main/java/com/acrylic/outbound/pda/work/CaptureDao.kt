package com.acrylic.outbound.pda.work

import androidx.room.*

@Dao
interface CaptureDao {
    @Insert fun insert(row: WorkCapture)
    @Query("SELECT * FROM work_capture WHERE id=:id") fun get(id: String): WorkCapture?
    @Query("SELECT * FROM work_capture WHERE assignmentId=:id AND state IN ('RECEIVED','WAITING_CONFIRM','CONFIRM_REQUESTED') ORDER BY orderScanSeq LIMIT 1")
    fun pending(id: String): WorkCapture?
    @Query("SELECT * FROM work_capture WHERE assignmentId=:id AND state IN ('INVALID','BUSINESS_REJECTED') AND failureAckAt IS NULL ORDER BY orderScanSeq LIMIT 1")
    fun failure(id: String): WorkCapture?
    @Query("SELECT COUNT(*) FROM work_capture WHERE assignmentId=:id AND state IN ('RECEIVED','WAITING_CONFIRM','CONFIRM_REQUESTED')")
    fun pendingCount(id: String): Int
    @Query("SELECT assignmentId FROM work_capture WHERE assignmentId!=:id AND (state IN ('RECEIVED','WAITING_CONFIRM','CONFIRM_REQUESTED') OR (state IN ('INVALID','BUSINESS_REJECTED') AND failureAckAt IS NULL)) LIMIT 1")
    fun unresolvedOther(id: String): String?
    @Query("UPDATE work_capture SET state=:state,error=:error,confirmedAt=:confirmedAt WHERE id=:id")
    fun progress(id: String, state: String, error: String, confirmedAt: String?): Int
    @Query("UPDATE work_capture SET failureAckAt=:at WHERE id=:id AND failureAckAt IS NULL AND state IN ('INVALID','BUSINESS_REJECTED')")
    fun acknowledge(id: String, at: String): Int
    @Query("SELECT * FROM work_capture WHERE assignmentId=:id AND orderScanSeq>:after ORDER BY orderScanSeq LIMIT :limit")
    fun page(id: String, after: Long, limit: Int): List<WorkCapture>
}
