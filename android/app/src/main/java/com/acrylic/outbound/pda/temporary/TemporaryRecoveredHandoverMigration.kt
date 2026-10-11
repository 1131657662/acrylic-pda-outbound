package com.acrylic.outbound.pda.temporary

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object TemporaryRecoveredHandoverMigration : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS temporary_recovered_handover_stage(
            caseId TEXT NOT NULL PRIMARY KEY,qualificationId TEXT NOT NULL,qualificationSha256 TEXT NOT NULL,
            recipientSessionSha256 TEXT NOT NULL,sourceRecoveryEpoch TEXT NOT NULL,targetRecoveryEpoch TEXT NOT NULL,
            userId TEXT NOT NULL,deviceId TEXT NOT NULL,warehouseId TEXT NOT NULL,userAuthVersion TEXT NOT NULL,
            deviceVersion TEXT NOT NULL,sessionId TEXT NOT NULL,localSessionId TEXT NOT NULL,manifestSha256 TEXT NOT NULL,
            manifestLength INTEGER NOT NULL,payloadSha256 TEXT NOT NULL,payloadLength INTEGER NOT NULL,
            signingKeyJson TEXT NOT NULL,state TEXT NOT NULL)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS temporary_recovered_handover_chunk(
            caseId TEXT NOT NULL,kind TEXT NOT NULL,offset INTEGER NOT NULL,bytes BLOB NOT NULL,
            PRIMARY KEY(caseId,kind,offset),FOREIGN KEY(caseId) REFERENCES temporary_recovered_handover_stage(caseId)
            ON UPDATE RESTRICT ON DELETE RESTRICT)""")
    }
}
