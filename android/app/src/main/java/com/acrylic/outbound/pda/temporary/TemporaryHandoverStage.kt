package com.acrylic.outbound.pda.temporary

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.ByteArrayOutputStream

/** Original TEMP facts remain separate from FORMAL assignments and mutable receiving work. */
@Entity(tableName = "temporary_handover_stage",
    indices = [Index(value = ["scopeId", "fromWorkRound"]), Index(value = ["targetAuthorizationId"])],
    foreignKeys = [ForeignKey(entity = TemporaryPreparation::class, parentColumns = ["authorizationId"],
        childColumns = ["targetAuthorizationId"])])
data class TemporaryHandoverStage(@PrimaryKey val caseId: String, val scopeId: String, val submissionId: String,
    val canonicalOrderId: String, val targetAuthorizationId: String, val targetUserId: String,
    val targetDeviceId: String, val warehouseId: String, val fromWorkRound: Long, val toWorkRound: Long,
    val inputHighWatermark: Long, val payloadSha256: String, val manifestJson: String,
    val signingKeyJson: String, val payloadBytes: ByteArray, val state: String = "STAGED")

@Dao
abstract class TemporaryHandoverStageDao {
    @Insert abstract fun insert(row: TemporaryHandoverStage)

    @Query("SELECT caseId FROM temporary_handover_stage WHERE targetUserId=:user AND targetDeviceId=:device AND warehouseId=:warehouse ORDER BY caseId DESC LIMIT :limit")
    abstract fun cases(user: String, device: String, warehouse: String, limit: Int): List<String>

    @Query("SELECT caseId,scopeId,submissionId,canonicalOrderId,targetAuthorizationId,targetUserId,targetDeviceId,warehouseId,fromWorkRound,toWorkRound,inputHighWatermark,payloadSha256,manifestJson,signingKeyJson,X'' AS payloadBytes,state FROM temporary_handover_stage WHERE caseId=:id")
    abstract fun header(id: String): TemporaryHandoverStage?

    @Query("SELECT length(payloadBytes) FROM temporary_handover_stage WHERE caseId=:id")
    protected abstract fun length(id: String): Int

    @Query("SELECT substr(payloadBytes,:offset,131072) FROM temporary_handover_stage WHERE caseId=:id")
    protected abstract fun chunk(id: String, offset: Int): ByteArray

    @Transaction open fun get(id: String): TemporaryHandoverStage? {
        val row = header(id) ?: return null
        val size = length(id)
        check(size in 1..32 * 1024 * 1024) { "临时交接原文长度错误，请保留资料" }
        val bytes = ByteArrayOutputStream(size)
        while (bytes.size() < size) {
            val part = chunk(id, bytes.size() + 1)
            check(part.isNotEmpty() && part.size <= size - bytes.size()) { "临时交接原文不完整" }
            bytes.write(part)
        }
        return row.copy(payloadBytes = bytes.toByteArray())
    }
}

object TemporaryHandoverStageMigration : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS temporary_handover_stage(
            caseId TEXT NOT NULL PRIMARY KEY,scopeId TEXT NOT NULL,submissionId TEXT NOT NULL,
            canonicalOrderId TEXT NOT NULL,targetAuthorizationId TEXT NOT NULL,targetUserId TEXT NOT NULL,
            targetDeviceId TEXT NOT NULL,warehouseId TEXT NOT NULL,fromWorkRound INTEGER NOT NULL,
            toWorkRound INTEGER NOT NULL,inputHighWatermark INTEGER NOT NULL,payloadSha256 TEXT NOT NULL,
            manifestJson TEXT NOT NULL,signingKeyJson TEXT NOT NULL,payloadBytes BLOB NOT NULL,state TEXT NOT NULL,
            FOREIGN KEY(targetAuthorizationId) REFERENCES temporary_preparation(authorizationId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_temporary_handover_stage_scopeId_fromWorkRound ON temporary_handover_stage(scopeId,fromWorkRound)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_temporary_handover_stage_targetAuthorizationId ON temporary_handover_stage(targetAuthorizationId)")
    }
}
