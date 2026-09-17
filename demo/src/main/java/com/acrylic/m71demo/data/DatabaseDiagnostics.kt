package com.acrylic.m71demo.data

object DatabaseDiagnostics {
    /** A transaction pins the primary writer; an idle PRAGMA can sample another pooled reader. */
    fun writer(db: DemoDatabase): WriterSettings {
        lateinit var result: WriterSettings
        db.runInTransaction {
            val connection = db.openHelper.writableDatabase
            val sync = connection.query("PRAGMA synchronous").use { it.moveToFirst(); it.getInt(0) }
            val mode = connection.query("PRAGMA journal_mode").use { it.moveToFirst(); it.getString(0) }
            val foreignKeys = connection.query("PRAGMA foreign_keys").use { it.moveToFirst(); it.getInt(0) }
            val checkpoint = connection.query("PRAGMA wal_autocheckpoint").use { it.moveToFirst(); it.getInt(0) }
            result = WriterSettings(sync, mode, foreignKeys, checkpoint)
        }
        return result
    }
}

data class WriterSettings(val synchronous: Int, val journalMode: String, val foreignKeys: Int,
    val walAutoCheckpointPages: Int)
