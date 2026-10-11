package com.acrylic.outbound.pda.temporary

import androidx.room.*
import java.io.ByteArrayOutputStream

@Entity(tableName = "temporary_recovered_handover_chunk", primaryKeys = ["caseId", "kind", "offset"],
    foreignKeys = [ForeignKey(entity = TemporaryRecoveredHandoverStage::class, parentColumns = ["caseId"],
        childColumns = ["caseId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT)])
data class TemporaryRecoveredHandoverChunk(val caseId: String, val kind: String, val offset: Int, val bytes: ByteArray)

/** Bounded rows avoid loading the original payload through a single CursorWindow. */
@Dao
abstract class TemporaryRecoveredHandoverChunkDao {
    @Insert protected abstract fun insert(row: TemporaryRecoveredHandoverChunk)
    @Query("SELECT * FROM temporary_recovered_handover_chunk WHERE caseId=:id AND kind=:kind ORDER BY offset")
    protected abstract fun chunks(id: String, kind: String): List<TemporaryRecoveredHandoverChunk>
    fun save(id: String, kind: String, bytes: ByteArray) {
        check(kind in setOf("MANIFEST", "PAYLOAD") && bytes.isNotEmpty())
        for (offset in bytes.indices step 131072)
            insert(TemporaryRecoveredHandoverChunk(id, kind, offset, bytes.copyOfRange(offset, minOf(offset + 131072, bytes.size))))
    }
    fun read(id: String, kind: String, size: Int): ByteArray {
        check(size in 1..32 * 1024 * 1024)
        val output = ByteArrayOutputStream(size)
        for (row in chunks(id, kind)) {
            check(row.offset == output.size() && row.bytes.isNotEmpty() && row.bytes.size <= 131072 && row.bytes.size <= size - output.size()) {
                "恢复交接原件分块不完整，请保留资料"
            }
            output.write(row.bytes)
        }
        check(output.size() == size) { "恢复交接原件长度错误，请保留资料" }
        return output.toByteArray()
    }
}
