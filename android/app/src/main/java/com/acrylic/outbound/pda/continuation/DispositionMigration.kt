package com.acrylic.outbound.pda.continuation

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DispositionMigration : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_disposition (captureId TEXT NOT NULL PRIMARY KEY,
            assignmentId TEXT NOT NULL, evidenceId TEXT NOT NULL, action TEXT NOT NULL, proofJson TEXT NOT NULL,
            ordinal INTEGER NOT NULL, FOREIGN KEY(captureId) REFERENCES work_scan(captureId) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE UNIQUE INDEX index_local_disposition_assignmentId_ordinal ON local_disposition(assignmentId,ordinal)")
        db.execSQL("CREATE UNIQUE INDEX index_local_disposition_evidenceId ON local_disposition(evidenceId)")
    }
}
