package com.acrylic.outbound.pda.temporary

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "temporary_archive_request", primaryKeys = ["submissionId", "recoveryEpoch"],
    foreignKeys = [ForeignKey(entity = TemporarySuccessEvidence::class, parentColumns = ["submissionId"], childColumns = ["submissionId"])])
data class TemporaryArchiveRequest(val submissionId: String, val recoveryEpoch: String, val requestJson: String, val contextJson: String)

@Entity(tableName = "temporary_archive_response", primaryKeys = ["submissionId", "recoveryEpoch", "responseSha256"],
    foreignKeys = [ForeignKey(entity = TemporaryArchiveRequest::class, parentColumns = ["submissionId", "recoveryEpoch"],
        childColumns = ["submissionId", "recoveryEpoch"])])
data class TemporaryArchiveResponse(val submissionId: String, val recoveryEpoch: String, val responseSha256: String,
    val responseBytes: ByteArray, val state: String = "RAW", val error: String = "")

@Entity(tableName = "temporary_archive_evidence", primaryKeys = ["submissionId", "recoveryEpoch"],
    indices = [Index(value = ["submissionId", "recoveryEpoch", "responseSha256"])],
    foreignKeys = [ForeignKey(entity = TemporaryArchiveResponse::class, parentColumns = ["submissionId", "recoveryEpoch", "responseSha256"],
        childColumns = ["submissionId", "recoveryEpoch", "responseSha256"])])
data class TemporaryArchiveEvidence(val submissionId: String, val recoveryEpoch: String, val responseSha256: String,
    val proofSha256: String, val signingKeyJson: String)

@Dao
interface TemporaryArchiveDao {
    @Query("SELECT * FROM temporary_archive_request WHERE submissionId=:id AND recoveryEpoch=:epoch")
    fun request(id: String, epoch: String): TemporaryArchiveRequest?
    @Insert fun insert(row: TemporaryArchiveRequest)
    @Query("SELECT * FROM temporary_archive_response WHERE submissionId=:id AND recoveryEpoch=:epoch AND responseSha256=:hash")
    fun response(id: String, epoch: String, hash: String): TemporaryArchiveResponse?
    @Insert fun insert(row: TemporaryArchiveResponse)
    @Query("UPDATE temporary_archive_response SET state=:state,error=:error WHERE submissionId=:id AND recoveryEpoch=:epoch AND responseSha256=:hash AND state!='VERIFIED'")
    fun result(id: String, epoch: String, hash: String, state: String, error: String)
    @Query("SELECT * FROM temporary_archive_evidence WHERE submissionId=:id AND recoveryEpoch=:epoch")
    fun evidence(id: String, epoch: String): TemporaryArchiveEvidence?
    @Insert fun accept(row: TemporaryArchiveEvidence)
}

object TemporaryArchiveMigration : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE temporary_archive_request(submissionId TEXT NOT NULL,recoveryEpoch TEXT NOT NULL,
            requestJson TEXT NOT NULL,contextJson TEXT NOT NULL,PRIMARY KEY(submissionId,recoveryEpoch),
            FOREIGN KEY(submissionId) REFERENCES temporary_success_evidence(submissionId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("""CREATE TABLE temporary_archive_response(submissionId TEXT NOT NULL,recoveryEpoch TEXT NOT NULL,
            responseSha256 TEXT NOT NULL,responseBytes BLOB NOT NULL,state TEXT NOT NULL,error TEXT NOT NULL,
            PRIMARY KEY(submissionId,recoveryEpoch,responseSha256),FOREIGN KEY(submissionId,recoveryEpoch)
            REFERENCES temporary_archive_request(submissionId,recoveryEpoch) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("""CREATE TABLE temporary_archive_evidence(submissionId TEXT NOT NULL,recoveryEpoch TEXT NOT NULL,
            responseSha256 TEXT NOT NULL,proofSha256 TEXT NOT NULL,signingKeyJson TEXT NOT NULL,PRIMARY KEY(submissionId,recoveryEpoch),
            FOREIGN KEY(submissionId,recoveryEpoch,responseSha256) REFERENCES temporary_archive_response(submissionId,recoveryEpoch,responseSha256)
            ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE INDEX index_temporary_archive_evidence_submissionId_recoveryEpoch_responseSha256 ON temporary_archive_evidence(submissionId,recoveryEpoch,responseSha256)")
    }
}
