package com.acrylic.outbound.pda.evidence

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** No historical request keys or receipts are invented during an upgrade. */
object LocalDraftTransferMigration : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_draft_transfer(clientOrderId TEXT NOT NULL PRIMARY KEY,
            evidenceId TEXT NOT NULL,requestCount INTEGER NOT NULL,requestSetSha256 TEXT NOT NULL,
            FOREIGN KEY(clientOrderId) REFERENCES local_draft_stop_signature(clientOrderId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_draft_request(clientOrderId TEXT NOT NULL,position INTEGER NOT NULL,
            operation TEXT NOT NULL,originalKey TEXT NOT NULL,requestJson TEXT NOT NULL,requestSha256 TEXT NOT NULL,
            PRIMARY KEY(clientOrderId,position),
            FOREIGN KEY(clientOrderId) REFERENCES local_draft_stop_signature(clientOrderId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_local_draft_request_originalKey ON local_draft_request(originalKey)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_draft_receipt(originalKey TEXT NOT NULL PRIMARY KEY,state TEXT NOT NULL,
            responseBytes BLOB,signingKeyJson TEXT,
            FOREIGN KEY(originalKey) REFERENCES local_draft_request(originalKey) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
    }
}
