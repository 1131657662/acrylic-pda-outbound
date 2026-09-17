package com.acrylic.outbound.pda.recall

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object RecallMigration : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_recall (
            id TEXT NOT NULL PRIMARY KEY, assignmentId TEXT NOT NULL, requestJson TEXT NOT NULL,
            packageId TEXT, payload TEXT, sha256 TEXT, state TEXT NOT NULL, proof TEXT)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_local_recall_assignmentId ON local_recall(assignmentId)")
    }
}
