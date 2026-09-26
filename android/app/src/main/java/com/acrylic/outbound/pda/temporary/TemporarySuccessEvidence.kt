package com.acrylic.outbound.pda.temporary

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "temporary_success_response", primaryKeys = ["submissionId", "responseSha256"],
    foreignKeys = [ForeignKey(entity = TemporarySubmission::class, parentColumns = ["id"], childColumns = ["submissionId"])])
data class TemporarySuccessResponse(val submissionId: String, val responseSha256: String, val responseBytes: ByteArray,
    val state: String = "RAW", val error: String = "")

@Entity(tableName = "temporary_success_evidence", indices = [Index(value = ["submissionId", "responseSha256"])],
    foreignKeys = [ForeignKey(entity = TemporarySuccessResponse::class, parentColumns = ["submissionId", "responseSha256"],
        childColumns = ["submissionId", "responseSha256"])])
data class TemporarySuccessEvidence(@PrimaryKey val submissionId: String, val responseSha256: String,
    val receiptSha256: String, val orderId: String, val signingKeyJson: String)

@Dao
interface TemporarySuccessDao {
    @Query("SELECT * FROM temporary_success_response WHERE submissionId=:id AND responseSha256=:hash")
    fun response(id: String, hash: String): TemporarySuccessResponse?
    @Insert fun insert(row: TemporarySuccessResponse)
    @Query("UPDATE temporary_success_response SET state=:state,error=:error WHERE submissionId=:id AND responseSha256=:hash AND state!='VERIFIED'")
    fun result(id: String, hash: String, state: String, error: String)
    @Query("SELECT * FROM temporary_success_evidence WHERE submissionId=:id") fun evidence(id: String): TemporarySuccessEvidence?
    @Insert fun accept(row: TemporarySuccessEvidence)
}

/** Append-only originals; no backfill can infer success for a pre-existing submission. */
object TemporarySuccessMigration : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE temporary_success_response(submissionId TEXT NOT NULL,responseSha256 TEXT NOT NULL,
            responseBytes BLOB NOT NULL,state TEXT NOT NULL,error TEXT NOT NULL,PRIMARY KEY(submissionId,responseSha256),
            FOREIGN KEY(submissionId) REFERENCES temporary_submission(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("""CREATE TABLE temporary_success_evidence(submissionId TEXT NOT NULL PRIMARY KEY,responseSha256 TEXT NOT NULL,
            receiptSha256 TEXT NOT NULL,orderId TEXT NOT NULL,signingKeyJson TEXT NOT NULL,
            FOREIGN KEY(submissionId,responseSha256) REFERENCES temporary_success_response(submissionId,responseSha256)
            ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE INDEX index_temporary_success_evidence_submissionId_responseSha256 ON temporary_success_evidence(submissionId,responseSha256)")
    }
}
