package com.acrylic.outbound.pda.independent

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object IndependentSkuMigration : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS independent_sku_original(
            sha256 TEXT NOT NULL PRIMARY KEY, byteLength INTEGER NOT NULL, parserVersion TEXT NOT NULL)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS independent_sku_chunk(
            sha256 TEXT NOT NULL, part INTEGER NOT NULL, bytes BLOB NOT NULL, PRIMARY KEY(sha256,part),
            FOREIGN KEY(sha256) REFERENCES independent_sku_original(sha256) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS independent_sku_import(
            id TEXT NOT NULL PRIMARY KEY, sha256 TEXT NOT NULL, displayName TEXT NOT NULL,
            selectedWall INTEGER NOT NULL, selectedElapsed INTEGER NOT NULL, selectedBoot INTEGER NOT NULL,
            FOREIGN KEY(sha256) REFERENCES independent_sku_original(sha256) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_independent_sku_import_sha256 ON independent_sku_import(sha256)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS independent_sku_decision(
            importId TEXT NOT NULL PRIMARY KEY, outcome TEXT NOT NULL, previousImportId TEXT,
            duplicatesConfirmed INTEGER NOT NULL, wall INTEGER NOT NULL, elapsed INTEGER NOT NULL, boot INTEGER NOT NULL,
            FOREIGN KEY(importId) REFERENCES independent_sku_import(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS independent_sku_active(
            slot INTEGER NOT NULL PRIMARY KEY, importId TEXT NOT NULL,
            FOREIGN KEY(importId) REFERENCES independent_sku_import(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_independent_sku_active_importId ON independent_sku_active(importId)")
    }
}
