package com.acrylic.outbound.pda.temporary

import androidx.room.*
import java.io.ByteArrayOutputStream

/** A recovery intent references retained originals, never an ordinary TEMP preparation. */
@Entity(tableName = "temporary_recovered_confirmation", indices = [Index(value = ["commandId"], unique = true)],
    foreignKeys = [ForeignKey(entity = TemporaryRecoveredHandoverStage::class, parentColumns = ["caseId"], childColumns = ["caseId"])])
data class TemporaryRecoveredConfirmation(@PrimaryKey val caseId: String, val commandId: String,
    val commandJson: String, val commandSha256: String, val state: String = "PENDING",
    val error: String = "", val autoRetry: Boolean = true)

@Entity(tableName = "temporary_recovered_confirmation_receipt", foreignKeys = [
    ForeignKey(entity = TemporaryRecoveredConfirmation::class, parentColumns = ["caseId"], childColumns = ["caseId"])])
data class TemporaryRecoveredConfirmationReceipt(@PrimaryKey val caseId: String, val receiptSha256: String,
    val signingKeyJson: String, val receiptBytes: ByteArray)

@Dao
abstract class TemporaryRecoveredConfirmationDao {
    @Insert abstract fun insert(row: TemporaryRecoveredConfirmation)
    @Insert abstract fun receipt(row: TemporaryRecoveredConfirmationReceipt)
    @Query("SELECT * FROM temporary_recovered_confirmation WHERE caseId=:id") abstract fun get(id: String): TemporaryRecoveredConfirmation?
    @Query("SELECT caseId FROM temporary_recovered_confirmation WHERE state='PENDING' AND autoRetry=1") abstract fun pending(): List<String>
    @Query("UPDATE temporary_recovered_confirmation SET state=:state,error=:error,autoRetry=:retry WHERE caseId=:id AND state!='CONFIRMED'")
    abstract fun progress(id: String, state: String, error: String, retry: Boolean): Int
    @Query("SELECT caseId,receiptSha256,signingKeyJson,X'' AS receiptBytes FROM temporary_recovered_confirmation_receipt WHERE caseId=:id")
    protected abstract fun header(id: String): TemporaryRecoveredConfirmationReceipt?
    @Query("SELECT length(receiptBytes) FROM temporary_recovered_confirmation_receipt WHERE caseId=:id") protected abstract fun length(id: String): Int
    @Query("SELECT substr(receiptBytes,:offset,131072) FROM temporary_recovered_confirmation_receipt WHERE caseId=:id")
    protected abstract fun chunk(id: String, offset: Int): ByteArray
    @Transaction open fun receipt(id: String): TemporaryRecoveredConfirmationReceipt? {
        val header = header(id) ?: return null; val size = length(id)
        check(size in 1..8 * 1024 * 1024) { "原恢复确认回执长度无效，请保留资料" }
        val bytes = ByteArrayOutputStream(size)
        while (bytes.size() < size) {
            val part = chunk(id, bytes.size() + 1)
            check(part.isNotEmpty() && part.size <= size - bytes.size()) { "原恢复确认回执不完整" }
            bytes.write(part)
        }
        return header.copy(receiptBytes = bytes.toByteArray())
    }
}
