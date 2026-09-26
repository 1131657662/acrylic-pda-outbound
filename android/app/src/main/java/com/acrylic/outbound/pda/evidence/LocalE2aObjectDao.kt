package com.acrylic.outbound.pda.evidence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import java.io.ByteArrayOutputStream

private const val E2A_OBJECT_HEADER = "SELECT clientOrderId,kind,recordId,objectId,deviceSeq,recordStatus," +
    "bytesSha256,X'' AS rawBytes,signingKeyJson FROM local_e2a_object "

/** Read large immutable bodies in bounded cursor chunks, including after process restart. */
@Dao
abstract class LocalE2aObjectDao {
    @Insert abstract fun insert(row: LocalE2aObject)
    @Transaction open fun page(id: String, kind: String, after: Long): List<LocalE2aObject> =
        headers(id, kind, after).map(::hydrate)
    @Transaction open fun frozen(id: String, submissionId: String): LocalE2aObject? =
        frozenHeader(id, submissionId)?.let(::hydrate)
    @Transaction open fun archives(id: String, after: String): List<LocalE2aObject> =
        archiveHeaders(id, after).map(::hydrate)
    @Transaction open fun byObject(id: String, objectId: String): LocalE2aObject? =
        header(id, objectId)?.let(::hydrate)

    @Query("SELECT COUNT(*) FROM local_e2a_object WHERE clientOrderId=:id")
    abstract fun count(id: String): Long
    @Query(E2A_OBJECT_HEADER + "WHERE clientOrderId=:id AND kind=:kind AND deviceSeq>:after ORDER BY deviceSeq LIMIT 128")
    protected abstract fun headers(id: String, kind: String, after: Long): List<LocalE2aObject>
    @Query(E2A_OBJECT_HEADER + "WHERE clientOrderId=:id AND kind='FROZEN' AND recordId=:submissionId")
    protected abstract fun frozenHeader(id: String, submissionId: String): LocalE2aObject?
    @Query(E2A_OBJECT_HEADER + "WHERE clientOrderId=:id AND kind='ARCHIVE' AND recordId>:after ORDER BY recordId LIMIT 128")
    protected abstract fun archiveHeaders(id: String, after: String): List<LocalE2aObject>
    @Query(E2A_OBJECT_HEADER + "WHERE clientOrderId=:id AND objectId=:objectId")
    protected abstract fun header(id: String, objectId: String): LocalE2aObject?
    @Query("SELECT length(rawBytes) FROM local_e2a_object WHERE objectId=:objectId")
    protected abstract fun length(objectId: String): Int
    @Query("SELECT substr(rawBytes,:offset,131072) FROM local_e2a_object WHERE objectId=:objectId")
    protected abstract fun part(objectId: String, offset: Int): ByteArray

    private fun hydrate(row: LocalE2aObject): LocalE2aObject {
        val size = length(row.objectId)
        check(size in 1..33554432) { "原机对象长度无效" }
        val out = ByteArrayOutputStream(size)
        var offset = 1
        while (offset <= size) {
            val bytes = part(row.objectId, offset)
            check(bytes.isNotEmpty() && bytes.size <= size - offset + 1)
            out.write(bytes)
            offset += bytes.size
        }
        check(out.size() == size)
        return row.copy(rawBytes = out.toByteArray())
    }
}
