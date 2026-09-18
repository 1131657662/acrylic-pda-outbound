package com.acrylic.outbound.pda.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AuthorizationMigration : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!hasColumn(db, "prepared_order", "authorizationHistoryJson"))
            db.execSQL("ALTER TABLE prepared_order ADD COLUMN authorizationHistoryJson TEXT NOT NULL DEFAULT '[]'")
        if (!hasColumn(db, "local_continuation", "authorizationHistoryJson"))
            db.execSQL("ALTER TABLE local_continuation ADD COLUMN authorizationHistoryJson TEXT NOT NULL DEFAULT '[]'")
    }

    private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean =
        db.query("PRAGMA table_info($table)").use { cursor ->
            val index = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) if (cursor.getString(index) == column) return@use true
            false
        }
}
