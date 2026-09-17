package com.acrylic.outbound.pda.submission

import androidx.room.*
import java.io.ByteArrayOutputStream

@Dao
abstract class SubmissionDao {
    // Large frozen evidence stays in the same durable row; bounded BLOB reads avoid CursorWindow limits.
    @Transaction open fun get(id: String): LocalSubmission? {
        val head = header(id) ?: return null
        val length = payloadLength(id)
        check(length in 1..32 * 1024 * 1024) { "冻结正文长度无效，请保留资料" }
        val bytes = ByteArrayOutputStream(length)
        var offset = 1
        while (offset <= length) {
            val part = payloadPart(id, offset)
            check(part.isNotEmpty()) { "冻结正文不完整，请保留资料" }
            bytes.write(part); offset += part.size
        }
        check(bytes.size() == length) { "冻结正文长度变化，请保留资料" }
        return head.copy(payload = bytes.toByteArray().toString(Charsets.UTF_8))
    }
    @Transaction open fun forOrder(id: String): LocalSubmission? = submissionId(id)?.let(::get)
    @Query("SELECT id,assignmentId,'' AS payload,sha256,state,receipt,error,autoRetry,currentMarker FROM local_submission WHERE id=:id")
    protected abstract fun header(id: String): LocalSubmission?
    @Query("SELECT id FROM local_submission WHERE assignmentId=:id AND currentMarker=1") protected abstract fun submissionId(id: String): String?
    @Query("SELECT length(CAST(payload AS BLOB)) FROM local_submission WHERE id=:id") protected abstract fun payloadLength(id: String): Int
    @Query("SELECT substr(CAST(payload AS BLOB),:offset,131072) FROM local_submission WHERE id=:id")
    protected abstract fun payloadPart(id: String, offset: Int): ByteArray
    @Query("UPDATE local_submission SET currentMarker=NULL,autoRetry=0 WHERE assignmentId=:id") abstract fun supersede(id: String)
    @Insert abstract fun insert(row: LocalSubmission)
    @Query("UPDATE local_submission SET state=:state,receipt=:receipt,error=:error,autoRetry=:retry WHERE id=:id")
    abstract fun progress(id: String, state: String, receipt: String?, error: String, retry: Boolean)
    @Query("UPDATE local_submission SET autoRetry=1 WHERE id=:id AND state NOT IN ('SYNCED','ABORTED','REJECTED')") abstract fun queue(id: String)
}
