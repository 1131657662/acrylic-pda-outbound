package com.acrylic.outbound.pda.temporary

import androidx.room.*
import java.io.ByteArrayOutputStream

@Entity(tableName = "temporary_handover_activation", indices = [Index(value = ["ticketId"], unique = true),
    Index(value = ["newClientOrderId"], unique = true), Index(value = ["authorizationId"], unique = true),
    Index(value = ["scopeId", "toWorkRound"], unique = true)], foreignKeys = [
    ForeignKey(entity = TemporaryHandoverConfirmation::class, parentColumns = ["caseId"], childColumns = ["caseId"]),
    ForeignKey(entity = TemporaryDraft::class, parentColumns = ["clientOrderId"], childColumns = ["newClientOrderId"]),
    ForeignKey(entity = TemporaryPreparation::class, parentColumns = ["authorizationId"], childColumns = ["authorizationId"])])
data class TemporaryHandoverActivation(@PrimaryKey val caseId: String, val ticketId: String, val scopeId: String,
    val toWorkRound: Long, val newClientOrderId: String, val authorizationId: String, val receiptSha256: String,
    val signingKeyJson: String, val receiptBytes: ByteArray)

@Dao
abstract class TemporaryHandoverActivationDao {
    @Insert abstract fun insert(row: TemporaryHandoverActivation)
    @Query("SELECT caseId,ticketId,scopeId,toWorkRound,newClientOrderId,authorizationId,receiptSha256,signingKeyJson,X'' AS receiptBytes FROM temporary_handover_activation WHERE caseId=:id")
    protected abstract fun header(id: String): TemporaryHandoverActivation?
    @Query("SELECT caseId FROM temporary_handover_activation WHERE newClientOrderId=:id") abstract fun caseForTarget(id: String): String?
    @Query("SELECT length(receiptBytes) FROM temporary_handover_activation WHERE caseId=:id") protected abstract fun length(id: String): Int
    @Query("SELECT substr(receiptBytes,:offset,131072) FROM temporary_handover_activation WHERE caseId=:id")
    protected abstract fun chunk(id: String, offset: Int): ByteArray

    @Transaction open fun get(id: String): TemporaryHandoverActivation? {
        val row = header(id) ?: return null
        val size = length(id); check(size in 1..8 * 1024 * 1024)
        val bytes = ByteArrayOutputStream(size)
        while (bytes.size() < size) {
            val part = chunk(id, bytes.size() + 1)
            check(part.isNotEmpty() && part.size <= size - bytes.size()) { "交接激活原回执不完整" }
            bytes.write(part)
        }
        return row.copy(receiptBytes = bytes.toByteArray())
    }
}
