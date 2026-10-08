package com.acrylic.outbound.pda.temporary

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object TemporaryHandoverActivationMigration : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS temporary_handover_activation(
            caseId TEXT NOT NULL PRIMARY KEY,ticketId TEXT NOT NULL,scopeId TEXT NOT NULL,toWorkRound INTEGER NOT NULL,
            newClientOrderId TEXT NOT NULL,authorizationId TEXT NOT NULL,receiptSha256 TEXT NOT NULL,
            signingKeyJson TEXT NOT NULL,receiptBytes BLOB NOT NULL,
            FOREIGN KEY(caseId) REFERENCES temporary_handover_confirmation(caseId) ON UPDATE NO ACTION ON DELETE NO ACTION,
            FOREIGN KEY(newClientOrderId) REFERENCES temporary_draft(clientOrderId) ON UPDATE NO ACTION ON DELETE NO ACTION,
            FOREIGN KEY(authorizationId) REFERENCES temporary_preparation(authorizationId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        for (field in listOf("ticketId", "newClientOrderId", "authorizationId"))
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_temporary_handover_activation_$field ON temporary_handover_activation($field)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_temporary_handover_activation_scopeId_toWorkRound ON temporary_handover_activation(scopeId,toWorkRound)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS temporary_handover_guard(
            barcode TEXT NOT NULL PRIMARY KEY,caseId TEXT NOT NULL,captureId TEXT NOT NULL,dispatchCycle INTEGER NOT NULL,
            recordSha256 TEXT NOT NULL,
            FOREIGN KEY(caseId) REFERENCES temporary_handover_activation(caseId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_temporary_handover_guard_caseId ON temporary_handover_guard(caseId)")
    }
}
