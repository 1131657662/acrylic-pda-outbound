package com.acrylic.outbound.pda.evidence

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.acrylic.outbound.pda.temporary.TemporaryDraft
import com.acrylic.outbound.pda.temporary.TemporarySubmission

@Entity(tableName = "local_e2a_stop", indices = [Index(value = ["submissionId"], unique = true)],
    foreignKeys = [ForeignKey(entity = TemporaryDraft::class, parentColumns = ["clientOrderId"], childColumns = ["clientOrderId"]),
        ForeignKey(entity = TemporarySubmission::class, parentColumns = ["id"], childColumns = ["submissionId"])])
data class LocalE2aStop(@PrimaryKey val clientOrderId: String, val submissionId: String,
    val authorizationId: String, val warehouseId: String, val originalOperatorId: String,
    val freezeDeviceId: String, val sourceRecoveryEpoch: Long, val snapshotId: String,
    val manifestId: String, val openOriginalKey: String, val stopEventId: String,
    val captureHighWatermark: Long, val auditThroughSeq: Long, val scanSnapshotCount: Long,
    val archiveSnapshotCount: Long,
    @androidx.room.ColumnInfo(defaultValue = "1") val workRound: Long = 1)

@Dao
interface LocalE2aStopDao {
    @Query("SELECT * FROM local_e2a_stop WHERE clientOrderId=:id") fun get(id: String): LocalE2aStop?
    @Insert fun insert(row: LocalE2aStop)
}

object LocalE2aStopMigration : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_e2a_stop(clientOrderId TEXT NOT NULL PRIMARY KEY,
            submissionId TEXT NOT NULL,authorizationId TEXT NOT NULL,warehouseId TEXT NOT NULL,
            originalOperatorId TEXT NOT NULL,freezeDeviceId TEXT NOT NULL,sourceRecoveryEpoch INTEGER NOT NULL,
            snapshotId TEXT NOT NULL,manifestId TEXT NOT NULL,openOriginalKey TEXT NOT NULL,
            stopEventId TEXT NOT NULL,captureHighWatermark INTEGER NOT NULL,auditThroughSeq INTEGER NOT NULL,
            scanSnapshotCount INTEGER NOT NULL,archiveSnapshotCount INTEGER NOT NULL,
            FOREIGN KEY(clientOrderId) REFERENCES temporary_draft(clientOrderId) ON UPDATE NO ACTION ON DELETE NO ACTION,
            FOREIGN KEY(submissionId) REFERENCES temporary_submission(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE UNIQUE INDEX index_local_e2a_stop_submissionId ON local_e2a_stop(submissionId)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_e2a_object(clientOrderId TEXT NOT NULL,
            kind TEXT NOT NULL,recordId TEXT NOT NULL,objectId TEXT NOT NULL,deviceSeq INTEGER NOT NULL,
            recordStatus TEXT NOT NULL,bytesSha256 TEXT NOT NULL,rawBytes BLOB NOT NULL,
            signingKeyJson TEXT,
            PRIMARY KEY(clientOrderId,kind,recordId),
            FOREIGN KEY(clientOrderId) REFERENCES local_e2a_stop(clientOrderId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE UNIQUE INDEX index_local_e2a_object_objectId ON local_e2a_object(objectId)")
        db.execSQL("CREATE INDEX index_local_e2a_object_clientOrderId_kind_deviceSeq ON local_e2a_object(clientOrderId,kind,deviceSeq)")
    }
}
