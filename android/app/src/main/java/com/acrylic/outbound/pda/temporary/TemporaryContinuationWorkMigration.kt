package com.acrylic.outbound.pda.temporary

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object TemporaryContinuationWorkMigration : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE local_e2a_stop ADD COLUMN workRound INTEGER NOT NULL DEFAULT 1")
        db.execSQL("""CREATE TABLE temporary_continuation_queue(ticketId TEXT NOT NULL PRIMARY KEY,
            state TEXT NOT NULL,error TEXT NOT NULL,autoRetry INTEGER NOT NULL,
            FOREIGN KEY(ticketId) REFERENCES temporary_continuation(ticketId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
    }
}
