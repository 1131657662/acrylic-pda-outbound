package com.acrylic.outbound.pda.temporary

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object TemporaryRecoveredConfirmationMigration : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS temporary_recovered_confirmation(
            caseId TEXT NOT NULL PRIMARY KEY,commandId TEXT NOT NULL,commandJson TEXT NOT NULL,
            commandSha256 TEXT NOT NULL,state TEXT NOT NULL,error TEXT NOT NULL,autoRetry INTEGER NOT NULL,
            FOREIGN KEY(caseId) REFERENCES temporary_recovered_handover_stage(caseId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_temporary_recovered_confirmation_commandId ON temporary_recovered_confirmation(commandId)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS temporary_recovered_confirmation_receipt(
            caseId TEXT NOT NULL PRIMARY KEY,receiptSha256 TEXT NOT NULL,signingKeyJson TEXT NOT NULL,receiptBytes BLOB NOT NULL,
            FOREIGN KEY(caseId) REFERENCES temporary_recovered_confirmation(caseId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
    }
}
