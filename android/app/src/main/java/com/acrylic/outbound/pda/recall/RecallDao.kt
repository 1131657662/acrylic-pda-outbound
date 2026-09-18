package com.acrylic.outbound.pda.recall

import androidx.room.*
import java.io.ByteArrayOutputStream

@Dao
abstract class RecallDao {
    @Query("SELECT id,assignmentId,requestJson,packageId,NULL AS payload,sha256,state,proof,currentMarker FROM local_recall WHERE assignmentId=:assignment AND currentMarker=1")
    abstract fun header(assignment: String): LocalRecall?
    @Query("SELECT length(CAST(payload AS BLOB)) FROM local_recall WHERE assignmentId=:assignment AND currentMarker=1")
    protected abstract fun length(assignment: String): Int?
    @Query("SELECT substr(CAST(payload AS BLOB),:offset,131072) FROM local_recall WHERE assignmentId=:assignment AND currentMarker=1")
    protected abstract fun part(assignment: String, offset: Int): ByteArray
    @Transaction open fun get(assignment: String): LocalRecall? {
        val head = header(assignment) ?: return null
        val size = length(assignment) ?: return head
        check(size in 1..32 * 1024 * 1024) { "恢复正文长度无效，请保留数据" }
        val bytes = ByteArrayOutputStream(size); var offset = 1
        while (offset <= size) {
            val part = part(assignment, offset); check(part.isNotEmpty()) { "恢复正文不完整" }
            bytes.write(part); offset += part.size
        }
        check(bytes.size() == size)
        return head.copy(payload = bytes.toByteArray().toString(Charsets.UTF_8))
    }
    @Query("UPDATE local_recall SET currentMarker=NULL WHERE assignmentId=:id") abstract fun supersede(id: String)
    @Query("UPDATE local_recall SET requestJson=:request WHERE id=:id") abstract fun request(id: String, request: String)
    @Insert abstract fun insert(row: LocalRecall)
    @Update abstract fun update(row: LocalRecall)
    @Query("SELECT COUNT(*) FROM local_recall WHERE state IN ('RECALL_PENDING','RECOVERY_FROZEN') AND currentMarker=1") abstract fun activeCount(): Int
}
