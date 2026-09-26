package com.acrylic.outbound.pda.temporary

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.ByteArrayOutputStream

@Entity(tableName = "temporary_submission", indices = [Index(value = ["clientOrderId"], unique = true)],
    foreignKeys = [ForeignKey(entity = TemporaryDraft::class, parentColumns = ["clientOrderId"], childColumns = ["clientOrderId"])])
data class TemporarySubmission(@PrimaryKey val id: String, val clientOrderId: String, val payload: String, val sha256: String,
    val state: String = "FROZEN", val receipt: String? = null, val error: String = "", val autoRetry: Boolean = false,
    val abortReason: String? = null)

@Dao
abstract class TemporarySubmissionDao {
    @Transaction open fun get(id: String): TemporarySubmission? {
        val row = header(id) ?: return null; val length = length(id)
        check(length in 1..32 * 1024 * 1024) { "原临时冻结正文长度无效" }
        val out = ByteArrayOutputStream(length); var offset = 1
        while (offset <= length) { val chunk = part(id, offset); check(chunk.isNotEmpty()); out.write(chunk); offset += chunk.size }
        check(out.size() == length); return row.copy(payload = out.toByteArray().toString(Charsets.UTF_8))
    }
    @Transaction open fun forOrder(id: String): TemporarySubmission? = submissionId(id)?.let(::get)
    @Query("SELECT id,clientOrderId,'' AS payload,sha256,state,receipt,error,autoRetry,abortReason FROM temporary_submission WHERE id=:id")
    protected abstract fun header(id: String): TemporarySubmission?
    @Query("SELECT id FROM temporary_submission WHERE clientOrderId=:id") protected abstract fun submissionId(id: String): String?
    @Query("SELECT length(CAST(payload AS BLOB)) FROM temporary_submission WHERE id=:id") protected abstract fun length(id: String): Int
    @Query("SELECT substr(CAST(payload AS BLOB),:offset,131072) FROM temporary_submission WHERE id=:id") protected abstract fun part(id: String, offset: Int): ByteArray
    @Insert abstract fun insert(row: TemporarySubmission)
    @Query("UPDATE temporary_submission SET state=:state,receipt=:receipt,error=:error,autoRetry=:retry WHERE id=:id")
    abstract fun progress(id: String, state: String, receipt: String?, error: String, retry: Boolean)
    @Query("UPDATE temporary_submission SET autoRetry=1 WHERE id=:id AND state NOT IN ('SYNCED','ABORTED','REJECTED')") abstract fun queue(id: String)
    @Query("UPDATE temporary_submission SET abortReason=:reason WHERE id=:id AND abortReason IS NULL") abstract fun abortIntent(id: String, reason: String)
    @Query("SELECT COUNT(*) FROM temporary_submission WHERE state IN ('RESULT_UNKNOWN','ACTION_REQUIRED') OR (abortReason IS NOT NULL AND state NOT IN ('ABORTED','SYNCED'))") abstract fun unknownCount(): Int
}

object TemporarySubmissionMigration : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE temporary_submission(id TEXT NOT NULL PRIMARY KEY,clientOrderId TEXT NOT NULL,
            payload TEXT NOT NULL,sha256 TEXT NOT NULL,state TEXT NOT NULL,receipt TEXT,error TEXT NOT NULL,autoRetry INTEGER NOT NULL,abortReason TEXT,
            FOREIGN KEY(clientOrderId) REFERENCES temporary_draft(clientOrderId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE UNIQUE INDEX index_temporary_submission_clientOrderId ON temporary_submission(clientOrderId)")
    }
}
