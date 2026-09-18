package com.acrylic.outbound.pda.continuation

import androidx.room.*
import java.io.ByteArrayOutputStream

@Entity(tableName = "local_continuation", indices = [Index(value = ["assignmentId", "currentMarker"], unique = true)])
data class LocalContinuation(@PrimaryKey val id: String, val assignmentId: String, val authorizationJson: String,
    val ticketJson: String, val workRound: Long, val nextOrderScanSeq: Long,
    val recoveryRequestId: String, val currentMarker: Int? = 1, val authorizationHistoryJson: String = "[]")

@Dao
abstract class ContinuationDao {
    @Query("SELECT id,assignmentId,authorizationJson,workRound,nextOrderScanSeq,recoveryRequestId,authorizationHistoryJson FROM local_continuation WHERE assignmentId=:id AND currentMarker=1")
    abstract fun current(id: String): ContinuationInfo?
    @Transaction open fun get(id: String): LocalContinuation? {
        val head = header(id) ?: return null; val length = ticketLength(id)
        check(length in 1..32 * 1024 * 1024) { "续作票据长度无效，请保留资料" }
        val bytes = ByteArrayOutputStream(length); var offset = 1
        while (offset <= length) {
            val part = ticketPart(id, offset); check(part.isNotEmpty()) { "续作票据不完整" }
            bytes.write(part); offset += part.size
        }
        check(bytes.size() == length)
        return head.copy(ticketJson = bytes.toByteArray().toString(Charsets.UTF_8))
    }
    @Query("SELECT id,assignmentId,authorizationJson,'' AS ticketJson,workRound,nextOrderScanSeq,recoveryRequestId,currentMarker,authorizationHistoryJson FROM local_continuation WHERE id=:id")
    protected abstract fun header(id: String): LocalContinuation?
    @Query("SELECT length(CAST(ticketJson AS BLOB)) FROM local_continuation WHERE id=:id") protected abstract fun ticketLength(id: String): Int
    @Query("SELECT substr(CAST(ticketJson AS BLOB),:offset,131072) FROM local_continuation WHERE id=:id") protected abstract fun ticketPart(id: String, offset: Int): ByteArray
    @Query("UPDATE local_continuation SET currentMarker=NULL WHERE assignmentId=:id") abstract fun supersede(id: String)
    @Query("UPDATE local_continuation SET authorizationJson=:current,authorizationHistoryJson=:history WHERE id=:id")
    abstract fun replaceAuthorization(id: String, current: String, history: String)
    @Insert abstract fun insert(row: LocalContinuation)
}

data class ContinuationInfo(val id: String, val assignmentId: String, val authorizationJson: String,
    val workRound: Long, val nextOrderScanSeq: Long, val recoveryRequestId: String, val authorizationHistoryJson: String)
