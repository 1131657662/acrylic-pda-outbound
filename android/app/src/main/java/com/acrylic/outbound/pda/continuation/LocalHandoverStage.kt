package com.acrylic.outbound.pda.continuation

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.ByteArrayOutputStream

@Entity(tableName = "local_handover_stage", indices = [Index(value = ["originalAssignmentId"], unique = true)])
data class LocalHandoverStage(@PrimaryKey val caseId: String, val originalAssignmentId: String,
    val orderId: String, val targetDeviceId: String, val targetUserId: String, val warehouseId: String,
    val inputHighWatermark: Long, val recoverySha256: String, val manifestJson: String,
    val signingKeyJson: String, val recoveryBytes: ByteArray, val state: String = "STAGED")

@Dao
abstract class LocalHandoverStageDao {
    @Query("SELECT caseId FROM local_handover_stage WHERE targetUserId=:user AND targetDeviceId=:device AND warehouseId=:warehouse ORDER BY caseId DESC LIMIT :limit")
    abstract fun cases(user: String, device: String, warehouse: String, limit: Int): List<String>

    @Insert abstract fun insert(row: LocalHandoverStage)
    @Update abstract fun update(row: LocalHandoverStage)

    @Query("SELECT caseId,originalAssignmentId,orderId,targetDeviceId,targetUserId,warehouseId,inputHighWatermark,recoverySha256,manifestJson,signingKeyJson,X'' AS recoveryBytes,state FROM local_handover_stage WHERE caseId=:caseId")
    abstract fun header(caseId: String): LocalHandoverStage?

    @Query("SELECT length(recoveryBytes) FROM local_handover_stage WHERE caseId=:caseId")
    protected abstract fun length(caseId: String): Int

    @Query("SELECT substr(recoveryBytes,:offset,131072) FROM local_handover_stage WHERE caseId=:caseId")
    protected abstract fun part(caseId: String, offset: Int): ByteArray

    @Transaction open fun get(caseId: String): LocalHandoverStage? {
        val row = header(caseId) ?: return null
        val size = length(caseId)
        check(size in 1..32 * 1024 * 1024) { "交接原包长度无效，请保留资料" }
        val bytes = ByteArrayOutputStream(size)
        var offset = 1
        while (offset <= size) {
            val chunk = part(caseId, offset)
            check(chunk.isNotEmpty()) { "交接原包不完整，请保留资料" }
            bytes.write(chunk)
            offset += chunk.size
        }
        check(bytes.size() == size)
        return row.copy(recoveryBytes = bytes.toByteArray())
    }
}

object LocalHandoverStageMigration : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_handover_stage(
            caseId TEXT NOT NULL PRIMARY KEY,originalAssignmentId TEXT NOT NULL,orderId TEXT NOT NULL,
            targetDeviceId TEXT NOT NULL,targetUserId TEXT NOT NULL,warehouseId TEXT NOT NULL,
            inputHighWatermark INTEGER NOT NULL,recoverySha256 TEXT NOT NULL,manifestJson TEXT NOT NULL,
            signingKeyJson TEXT NOT NULL,recoveryBytes BLOB NOT NULL,state TEXT NOT NULL)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_local_handover_stage_originalAssignmentId ON local_handover_stage(originalAssignmentId)")
    }
}
