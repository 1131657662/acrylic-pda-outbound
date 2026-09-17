package com.acrylic.outbound.pda.submission

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object SubmissionMigration : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_submission (id TEXT NOT NULL PRIMARY KEY,
            assignmentId TEXT NOT NULL, payload TEXT NOT NULL, sha256 TEXT NOT NULL, state TEXT NOT NULL,
            receipt TEXT, error TEXT NOT NULL, autoRetry INTEGER NOT NULL,
            FOREIGN KEY(assignmentId) REFERENCES work_order(assignmentId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_local_submission_assignmentId ON local_submission(assignmentId)")
    }
}
