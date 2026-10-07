package com.acrylic.outbound.pda.temporary

import androidx.room.*
import java.io.ByteArrayOutputStream

/** The original recipient intent survives transport failure; progress never rewrites its command. */
@Entity(tableName = "temporary_handover_confirmation", indices = [Index(value = ["commandId"], unique = true)],
    foreignKeys = [ForeignKey(entity = TemporaryHandoverStage::class, parentColumns = ["caseId"], childColumns = ["caseId"])])
data class TemporaryHandoverConfirmation(@PrimaryKey val caseId: String, val commandId: String,
    val boundCommandJson: String, val commandSha256: String, val state: String = "PENDING",
    val error: String = "", val autoRetry: Boolean = true)

@Entity(tableName = "temporary_handover_confirmation_receipt", foreignKeys = [
    ForeignKey(entity = TemporaryHandoverConfirmation::class, parentColumns = ["caseId"], childColumns = ["caseId"])])
data class TemporaryHandoverConfirmationReceipt(@PrimaryKey val caseId: String, val receiptSha256: String,
    val signingKeyJson: String, val receiptBytes: ByteArray)

@Dao
abstract class TemporaryHandoverConfirmationDao {
    @Insert abstract fun insert(row: TemporaryHandoverConfirmation)
    @Insert abstract fun receipt(row: TemporaryHandoverConfirmationReceipt)
    @Query("SELECT * FROM temporary_handover_confirmation WHERE caseId=:id") abstract fun get(id: String): TemporaryHandoverConfirmation?
    @Query("SELECT caseId FROM temporary_handover_confirmation WHERE state='PENDING' AND autoRetry=1") abstract fun pending(): List<String>
    @Query("UPDATE temporary_handover_confirmation SET state=:state,error=:error,autoRetry=:retry WHERE caseId=:id")
    abstract fun progress(id: String, state: String, error: String, retry: Boolean): Int
    @Query("SELECT caseId,receiptSha256,signingKeyJson,X'' AS receiptBytes FROM temporary_handover_confirmation_receipt WHERE caseId=:id")
    protected abstract fun receiptHeader(id: String): TemporaryHandoverConfirmationReceipt?
    @Query("SELECT length(receiptBytes) FROM temporary_handover_confirmation_receipt WHERE caseId=:id") protected abstract fun length(id: String): Int
    @Query("SELECT substr(receiptBytes,:offset,131072) FROM temporary_handover_confirmation_receipt WHERE caseId=:id")
    protected abstract fun chunk(id: String, offset: Int): ByteArray

    @Transaction open fun receipt(id: String): TemporaryHandoverConfirmationReceipt? {
        val header = receiptHeader(id) ?: return null
        val size = length(id)
        check(size in 1..8 * 1024 * 1024) { "原交接确认回执长度无效，保留资料" }
        val bytes = ByteArrayOutputStream(size)
        while (bytes.size() < size) {
            val part = chunk(id, bytes.size() + 1)
            check(part.isNotEmpty() && part.size <= size - bytes.size()) { "原交接确认回执不完整" }
            bytes.write(part)
        }
        return header.copy(receiptBytes = bytes.toByteArray())
    }
}
