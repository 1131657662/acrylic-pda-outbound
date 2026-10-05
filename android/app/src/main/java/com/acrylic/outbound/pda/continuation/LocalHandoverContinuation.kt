package com.acrylic.outbound.pda.continuation

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** One durable original command per imported case; a failed request never replaces its key. */
@Entity(tableName = "local_handover_continuation", indices = [Index(value = ["commandId"], unique = true)],
    foreignKeys = [ForeignKey(entity = LocalHandoverStage::class, parentColumns = ["caseId"], childColumns = ["caseId"])])
data class LocalHandoverContinuation(@PrimaryKey val caseId: String, val commandId: String,
    val commandJson: String, val commandSha256: String, val identityJson: String,
    val continuationId: String? = null)

@Dao
interface HandoverContinuationDao {
    @Query("SELECT * FROM local_handover_continuation WHERE caseId=:id") fun get(id: String): LocalHandoverContinuation?
    @Insert fun insert(row: LocalHandoverContinuation)
    @Query("UPDATE local_handover_continuation SET continuationId=:continuation WHERE caseId=:id AND continuationId IS NULL")
    fun applied(id: String, continuation: String): Int
}

object HandoverContinuationMigration : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_handover_continuation(
            caseId TEXT NOT NULL PRIMARY KEY,commandId TEXT NOT NULL,commandJson TEXT NOT NULL,
            commandSha256 TEXT NOT NULL,identityJson TEXT NOT NULL,continuationId TEXT,
            FOREIGN KEY(caseId) REFERENCES local_handover_stage(caseId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_local_handover_continuation_commandId ON local_handover_continuation(commandId)")
        val hasPosition = db.query("PRAGMA table_info(work_event)").use { rows ->
            var found = false
            while (rows.moveToNext()) if (rows.getString(1) == "orderPosition") found = true
            found
        }
        if (!hasPosition) {
            db.execSQL("ALTER TABLE work_event ADD COLUMN orderPosition INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE work_event SET orderPosition=localSeq")
        }
        db.execSQL("CREATE INDEX IF NOT EXISTS index_work_event_assignmentId_orderPosition ON work_event(assignmentId,orderPosition)")
    }
}
