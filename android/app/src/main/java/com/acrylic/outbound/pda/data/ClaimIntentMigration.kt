package com.acrylic.outbound.pda.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object ClaimIntentMigration : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!hasColumn(db, "status")) db.execSQL("ALTER TABLE claim_intent ADD COLUMN status TEXT NOT NULL DEFAULT 'UNKNOWN'")
        if (!hasColumn(db, "lastError")) db.execSQL("ALTER TABLE claim_intent ADD COLUMN lastError TEXT")
    }

    private fun hasColumn(database: SupportSQLiteDatabase, name: String): Boolean =
        database.query("PRAGMA table_info(claim_intent)").use { cursor ->
            val index = cursor.getColumnIndex("name")
            index >= 0 && generateSequence { if (cursor.moveToNext()) cursor.getString(index) else null }.any { it == name }
        }
}
