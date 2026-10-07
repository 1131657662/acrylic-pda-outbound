package com.acrylic.outbound.pda.temporary

import androidx.room.*

@Entity(tableName = "temporary_continuation_queue", foreignKeys = [
    ForeignKey(entity = TemporaryContinuation::class, parentColumns = ["ticketId"], childColumns = ["ticketId"])])
data class TemporaryContinuationQueue(@PrimaryKey val ticketId: String, val state: String = "PENDING",
    val error: String = "", val autoRetry: Boolean = true)

@Dao
interface TemporaryContinuationQueueDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insert(row: TemporaryContinuationQueue): Long
    @Query("SELECT * FROM temporary_continuation_queue WHERE ticketId=:id") fun get(id: String): TemporaryContinuationQueue?
    @Query("SELECT * FROM temporary_continuation_queue WHERE autoRetry=1 AND state='PENDING'")
    fun pending(): List<TemporaryContinuationQueue>
    @Query("UPDATE temporary_continuation_queue SET state=:state,error=:error,autoRetry=:retry WHERE ticketId=:id")
    fun progress(id: String, state: String, error: String, retry: Boolean): Int
}
