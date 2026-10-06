package com.acrylic.outbound.pda.temporary

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object TemporaryContinuationMigration : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE temporary_draft ADD COLUMN workRound INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE temporary_draft ADD COLUMN authorizationHistoryJson TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("""CREATE TABLE temporary_continuation(ticketId TEXT NOT NULL PRIMARY KEY,
            sourceClientOrderId TEXT NOT NULL,sourceSubmissionId TEXT NOT NULL,sourcePayloadSha256 TEXT NOT NULL,
            newClientOrderId TEXT NOT NULL,receiptJson TEXT NOT NULL,
            FOREIGN KEY(sourceClientOrderId) REFERENCES temporary_draft(clientOrderId) ON UPDATE NO ACTION ON DELETE NO ACTION,
            FOREIGN KEY(sourceSubmissionId) REFERENCES temporary_submission(id) ON UPDATE NO ACTION ON DELETE NO ACTION,
            FOREIGN KEY(newClientOrderId) REFERENCES temporary_draft(clientOrderId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        for (column in listOf("sourceClientOrderId", "sourceSubmissionId", "newClientOrderId"))
            db.execSQL("CREATE UNIQUE INDEX index_temporary_continuation_$column ON temporary_continuation($column)")
    }
}
