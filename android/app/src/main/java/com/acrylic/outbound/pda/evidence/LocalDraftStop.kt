package com.acrylic.outbound.pda.evidence

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.acrylic.outbound.pda.temporary.TemporaryDraft

/** Local preservation only: evidenceId is not a submission or a server receipt. */
@Entity(tableName = "local_draft_stop", foreignKeys = [ForeignKey(entity = TemporaryDraft::class,
    parentColumns = ["clientOrderId"], childColumns = ["clientOrderId"])])
data class LocalDraftStop(@PrimaryKey val clientOrderId: String, val evidenceId: String,
    val metadataJson: String, val rootSha256: String, val recordCount: Long, val totalBytes: Long)

@Entity(tableName = "local_draft_stop_item", primaryKeys = ["clientOrderId", "ordinal"],
    foreignKeys = [ForeignKey(entity = LocalDraftStop::class, parentColumns = ["clientOrderId"],
        childColumns = ["clientOrderId"])])
data class LocalDraftStopItem(val clientOrderId: String, val ordinal: Long, val kind: String,
    val recordId: String, val deviceSeq: Long, val recordStatus: String,
    val bytesSha256: String, val rawBytes: ByteArray)

object LocalDraftStopMigration : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_draft_stop(
            clientOrderId TEXT NOT NULL PRIMARY KEY,evidenceId TEXT NOT NULL,metadataJson TEXT NOT NULL,
            rootSha256 TEXT NOT NULL,recordCount INTEGER NOT NULL,totalBytes INTEGER NOT NULL,
            FOREIGN KEY(clientOrderId) REFERENCES temporary_draft(clientOrderId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_draft_stop_item(
            clientOrderId TEXT NOT NULL,ordinal INTEGER NOT NULL,kind TEXT NOT NULL,recordId TEXT NOT NULL,
            deviceSeq INTEGER NOT NULL,recordStatus TEXT NOT NULL,bytesSha256 TEXT NOT NULL,rawBytes BLOB NOT NULL,
            PRIMARY KEY(clientOrderId,ordinal),
            FOREIGN KEY(clientOrderId) REFERENCES local_draft_stop(clientOrderId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
    }
}
