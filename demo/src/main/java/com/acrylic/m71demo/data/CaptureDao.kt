package com.acrylic.m71demo.data

import androidx.room.*

@Dao
interface CaptureDao {
    @Insert fun insert(capture: InputCapture)
    @Insert fun insertBatch(captures: List<InputCapture>)
    @Update fun update(capture: InputCapture)
    @Update(entity = InputCapture::class) fun updateProgressFields(progress: CaptureProgress): Int
    fun updateProgress(capture: InputCapture) {
        check(updateProgressFields(CaptureProgress(capture.id, capture.state, capture.error,
            capture.confirmedAtNs, capture.failureAcknowledged, capture.inputCommittedNs,
            capture.confirmCommittedNs)) == 1) { "Original capture missing; stop processing" }
    }
    @Query("SELECT * FROM local_input_capture WHERE id=:id") fun get(id: String): InputCapture?
    @Query("SELECT * FROM local_input_capture INDEXED BY index_local_input_capture_orderId_state_orderScanSeq WHERE orderId=:id AND state IN ('RECEIVED','WAITING_CONFIRM','CONFIRM_REQUESTED','STORAGE_ERROR') ORDER BY orderScanSeq LIMIT 1")
    fun pending(id: String): InputCapture?
    @Query("SELECT * FROM local_input_capture INDEXED BY index_local_input_capture_orderId_state_orderScanSeq WHERE orderId=:id AND state IN ('INVALID','BUSINESS_REJECTED') AND failureAcknowledged=0 ORDER BY orderScanSeq LIMIT 1")
    fun failure(id: String): InputCapture?
    @Query("SELECT * FROM local_input_capture WHERE orderId=:id AND state IN ('CONFIRM_REQUESTED','STORAGE_ERROR') ORDER BY orderScanSeq LIMIT 1")
    fun confirmed(id: String): InputCapture?
    @Query("SELECT * FROM local_input_capture WHERE orderId=:id AND state='RECEIVED' ORDER BY orderScanSeq LIMIT 1")
    fun received(id: String): InputCapture?
    @Query("SELECT COUNT(*) FROM local_input_capture WHERE orderId=:id AND state IN ('RECEIVED','WAITING_CONFIRM','CONFIRM_REQUESTED','STORAGE_ERROR')")
    fun pendingCount(id: String): Int
    @Query("SELECT * FROM local_input_capture WHERE orderId=:id AND orderScanSeq>:after ORDER BY orderScanSeq LIMIT :limit")
    fun page(id: String, after: Long, limit: Int): List<InputCapture>
    @Query("SELECT COUNT(*) FROM local_input_capture WHERE orderId=:id") fun count(id: String): Int
}
