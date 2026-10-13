package com.acrylic.outbound.pda.independent

import androidx.sqlite.db.SupportSQLiteDatabase

internal object IndependentScanConstraints {
    fun install(db: SupportSQLiteDatabase) {
        listOf("independent_capture", "independent_scan", "independent_void").forEach { table ->
            listOf("UPDATE", "DELETE").forEach { action ->
                db.execSQL("""CREATE TRIGGER IF NOT EXISTS immutable_${table}_${action.lowercase()} BEFORE $action ON $table
                    BEGIN SELECT RAISE(ABORT,'independent scan original is immutable'); END""")
            }
        }
        listOf("independent_capture_state", "independent_device_state").forEach { table ->
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS immutable_${table}_delete BEFORE DELETE ON $table
                BEGIN SELECT RAISE(ABORT,'independent processing state must be preserved'); END""")
        }
        crossSourceGuards(db); crossSourceInputs(db)
    }
    private fun crossSourceGuards(db: SupportSQLiteDatabase) {
        val oldGuards = listOf("work_guard", "temporary_guard", "temporary_handover_guard")
        oldGuards.forEach { table ->
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS ${table}_independent_conflict BEFORE INSERT ON $table
                WHEN EXISTS (SELECT 1 FROM independent_guard WHERE barcode=NEW.barcode)
                BEGIN SELECT RAISE(ABORT,'independent source already owns this barcode'); END""")
        }
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS independent_guard_source_conflict BEFORE INSERT ON independent_guard
            WHEN ${oldGuards.joinToString(" OR ") { "EXISTS (SELECT 1 FROM $it WHERE barcode=NEW.barcode)" }}
            BEGIN SELECT RAISE(ABORT,'formal or temporary source already owns this barcode'); END""")
    }
    private fun crossSourceInputs(db: SupportSQLiteDatabase) {
        val unresolved = """EXISTS (SELECT 1 FROM independent_capture_state WHERE state IN ('RECEIVED','WAITING_CONFIRM','CONFIRM_REQUESTED')
            OR (state IN ('INVALID','BUSINESS_REJECTED') AND failureAckAt IS NULL))"""
        listOf("work_capture", "temporary_capture").forEach { table ->
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS ${table}_independent_pending BEFORE INSERT ON $table WHEN $unresolved
                BEGIN SELECT RAISE(ABORT,'independent input requires recovery first'); END""")
        }
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS independent_capture_other_pending BEFORE INSERT ON independent_capture
            WHEN EXISTS (SELECT 1 FROM work_capture WHERE state IN ('RECEIVED','WAITING_CONFIRM','CONFIRM_REQUESTED')
                OR (state IN ('INVALID','BUSINESS_REJECTED') AND failureAckAt IS NULL))
            OR EXISTS (SELECT 1 FROM temporary_capture WHERE state IN ('RECEIVED','WAITING_CONFIRM','CONFIRM_REQUESTED')
                OR (state IN ('INVALID','BUSINESS_REJECTED') AND failureAckAt IS NULL))
            BEGIN SELECT RAISE(ABORT,'formal or temporary input requires recovery first'); END""")
    }
}
