package com.acrylic.outbound.pda.temporary

import androidx.room.*

/** Append-only local consumption proof. The original draft and submission are never rewritten. */
@Entity(tableName = "temporary_continuation", indices = [
    Index(value = ["sourceClientOrderId"], unique = true), Index(value = ["sourceSubmissionId"], unique = true),
    Index(value = ["newClientOrderId"], unique = true)], foreignKeys = [
    ForeignKey(entity = TemporaryDraft::class, parentColumns = ["clientOrderId"], childColumns = ["sourceClientOrderId"]),
    ForeignKey(entity = TemporarySubmission::class, parentColumns = ["id"], childColumns = ["sourceSubmissionId"]),
    ForeignKey(entity = TemporaryDraft::class, parentColumns = ["clientOrderId"], childColumns = ["newClientOrderId"])])
data class TemporaryContinuation(@PrimaryKey val ticketId: String, val sourceClientOrderId: String,
    val sourceSubmissionId: String, val sourcePayloadSha256: String, val newClientOrderId: String,
    val receiptJson: String)

@Dao
interface TemporaryContinuationDao {
    @Insert fun insert(row: TemporaryContinuation)
    @Query("SELECT * FROM temporary_continuation WHERE ticketId=:id") fun get(id: String): TemporaryContinuation?
    @Query("SELECT * FROM temporary_continuation WHERE sourceClientOrderId=:id") fun forSource(id: String): TemporaryContinuation?
}
