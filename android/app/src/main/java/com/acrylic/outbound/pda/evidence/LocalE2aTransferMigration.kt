package com.acrylic.outbound.pda.evidence

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Old frozen records remain intact; an upgrade cannot invent their original request keys. */
object LocalE2aTransferMigration : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_e2a_transfer(clientOrderId TEXT NOT NULL PRIMARY KEY,
            manifestJson TEXT NOT NULL,manifestBytesHash TEXT NOT NULL,setHash TEXT NOT NULL,
            stopOriginalKey TEXT NOT NULL,verifyOriginalKey TEXT NOT NULL,pageCount INTEGER NOT NULL,rawCount INTEGER NOT NULL,
            requestSetSha256 TEXT NOT NULL,devicePublicKey TEXT NOT NULL,
            FOREIGN KEY(clientOrderId) REFERENCES local_e2a_stop(clientOrderId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_e2a_request(clientOrderId TEXT NOT NULL,
            operation TEXT NOT NULL,ordinal INTEGER NOT NULL,originalKey TEXT NOT NULL,
            requestJson TEXT NOT NULL,requestSha256 TEXT NOT NULL,
            PRIMARY KEY(clientOrderId,operation,ordinal),
            FOREIGN KEY(clientOrderId) REFERENCES local_e2a_stop(clientOrderId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE UNIQUE INDEX index_local_e2a_request_originalKey ON local_e2a_request(originalKey)")
    }
}
