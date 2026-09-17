package com.acrylic.outbound.pda.continuation

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object ContinuationMigration : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE work_capture ADD COLUMN originAssignmentId TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE work_capture SET originAssignmentId=assignmentId")
        db.execSQL("ALTER TABLE work_event ADD COLUMN originAssignmentId TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE work_event SET originAssignmentId=assignmentId")
        for (table in listOf("local_submission", "local_recall")) {
            db.execSQL("ALTER TABLE $table ADD COLUMN currentMarker INTEGER DEFAULT 1")
            db.execSQL("DROP INDEX index_${table}_assignmentId")
            db.execSQL("CREATE UNIQUE INDEX index_${table}_assignmentId_currentMarker ON $table(assignmentId,currentMarker)")
        }
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_continuation (id TEXT NOT NULL PRIMARY KEY, assignmentId TEXT NOT NULL,
            authorizationJson TEXT NOT NULL, ticketJson TEXT NOT NULL, workRound INTEGER NOT NULL, nextOrderScanSeq INTEGER NOT NULL,
            recoveryRequestId TEXT NOT NULL, currentMarker INTEGER)""")
        db.execSQL("CREATE UNIQUE INDEX index_local_continuation_assignmentId_currentMarker ON local_continuation(assignmentId,currentMarker)")
    }
}
