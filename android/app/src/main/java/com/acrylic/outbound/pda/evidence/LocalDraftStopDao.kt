package com.acrylic.outbound.pda.evidence

import androidx.room.*
import java.io.ByteArrayOutputStream

@Dao
abstract class LocalDraftStopDao {
    @Insert abstract fun insert(row: LocalDraftStop)
    @Insert abstract fun insertItem(row: LocalDraftStopItem)
    @Query("SELECT * FROM local_draft_stop WHERE clientOrderId=:id")
    abstract fun get(id: String): LocalDraftStop?
    @Query("""UPDATE local_draft_stop SET rootSha256=:root,recordCount=:count,totalBytes=:bytes
        WHERE clientOrderId=:id AND rootSha256='' AND recordCount=0 AND totalBytes=0""")
    abstract fun seal(id: String, root: String, count: Long, bytes: Long): Int
    @Query("SELECT COUNT(*) FROM local_draft_stop_item WHERE clientOrderId=:id")
    abstract fun count(id: String): Long
    @Query("""SELECT clientOrderId,ordinal,kind,recordId,deviceSeq,recordStatus,bytesSha256,X'' AS rawBytes
        FROM local_draft_stop_item WHERE clientOrderId=:id AND ordinal>:after ORDER BY ordinal LIMIT 128""")
    protected abstract fun headers(id: String, after: Long): List<LocalDraftStopItem>
    @Query("SELECT length(rawBytes) FROM local_draft_stop_item WHERE clientOrderId=:id AND ordinal=:ordinal")
    protected abstract fun length(id: String, ordinal: Long): Int
    @Query("SELECT substr(rawBytes,:offset,131072) FROM local_draft_stop_item WHERE clientOrderId=:id AND ordinal=:ordinal")
    protected abstract fun part(id: String, ordinal: Long, offset: Int): ByteArray

    fun page(id: String, after: Long): List<LocalDraftStopItem> = headers(id, after).map { row ->
        val size = length(id, row.ordinal)
        check(size in 1..33554432)
        val out = ByteArrayOutputStream(size)
        var offset = 1
        while (offset <= size) {
            val bytes = part(id, row.ordinal, offset)
            check(bytes.isNotEmpty() && bytes.size <= size - offset + 1)
            out.write(bytes); offset += bytes.size
        }
        row.copy(rawBytes = out.toByteArray())
    }
}
