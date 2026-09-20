package com.acrylic.outbound.pda.retention

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.acrylic.outbound.pda.submission.LocalSubmission

/** A durable server-copy confirmation, never permission to delete local business evidence. */
@Entity(tableName = "local_submission_archive", primaryKeys = ["submissionId", "recoveryEpoch"],
    foreignKeys = [ForeignKey(entity = LocalSubmission::class, parentColumns = ["id"], childColumns = ["submissionId"])])
data class SubmissionArchive(val submissionId: String, val recoveryEpoch: String, val payloadSha256: String,
    val receiptSha256: String, val confirmationJson: String, val signingKeyJson: String, val confirmedAt: Long)

@Dao
interface SubmissionArchiveDao {
    @Query("SELECT * FROM local_submission_archive WHERE submissionId=:id AND recoveryEpoch=:epoch")
    fun get(id: String, epoch: String): SubmissionArchive?
    @Insert fun insert(row: SubmissionArchive)
    @Query("""SELECT s.id FROM local_submission s INNER JOIN prepared_order p ON p.assignmentId=s.assignmentId
        WHERE s.state='SYNCED' AND p.operatorId=:operator AND p.deviceId=:device
        AND NOT EXISTS(SELECT 1 FROM local_submission_archive a WHERE a.submissionId=s.id AND a.recoveryEpoch=:epoch)
        ORDER BY s.id LIMIT 10""")
    fun pending(operator: String, device: String, epoch: String): List<String>
}

object SubmissionArchiveMigration : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_submission_archive(
            submissionId TEXT NOT NULL, recoveryEpoch TEXT NOT NULL, payloadSha256 TEXT NOT NULL,
            receiptSha256 TEXT NOT NULL, confirmationJson TEXT NOT NULL, signingKeyJson TEXT NOT NULL,
            confirmedAt INTEGER NOT NULL, PRIMARY KEY(submissionId,recoveryEpoch),
            FOREIGN KEY(submissionId) REFERENCES local_submission(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
    }
}
