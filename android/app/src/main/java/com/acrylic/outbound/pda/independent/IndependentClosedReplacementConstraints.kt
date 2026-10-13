package com.acrylic.outbound.pda.independent

import androidx.sqlite.db.SupportSQLiteDatabase

/** Protect conflict targets even when SQLite's replacement DELETE triggers are disabled. */
internal object IndependentClosedReplacementConstraints {
    fun install(db: SupportSQLiteDatabase) {
        originals(db); guards(db)
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS independent_task_state_closed_target BEFORE UPDATE ON independent_task_state
            WHEN EXISTS(SELECT 1 FROM independent_task_state WHERE taskId=NEW.taskId AND state='SEALED')
            BEGIN SELECT RAISE(ABORT,'sealed independent state must be preserved'); END""")
    }
    private fun originals(db: SupportSQLiteDatabase) {
        val conflicts = mapOf(
            "independent_capture" to "(r.id=NEW.id OR (r.deviceId=NEW.deviceId AND r.deviceSequence=NEW.deviceSequence))",
            "independent_scan" to "r.captureId=NEW.captureId",
            "independent_void" to "r.captureId=NEW.captureId",
            "independent_task_event" to "r.id=NEW.id")
        conflicts.forEach { (table, conflict) ->
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS ${table}_closed_replacement BEFORE INSERT ON $table
                WHEN EXISTS(SELECT 1 FROM $table r INNER JOIN independent_task_state s ON s.taskId=r.taskId
                    WHERE $conflict AND s.state='SEALED')
                BEGIN SELECT RAISE(ABORT,'sealed independent original must be preserved'); END""")
        }
    }
    private fun guards(db: SupportSQLiteDatabase) {
        listOf("INSERT", "UPDATE").forEach { action ->
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS independent_guard_closed_replacement_${action.lowercase()}
                BEFORE $action ON independent_guard
                WHEN EXISTS(SELECT 1 FROM independent_guard g INNER JOIN independent_task_state s ON s.taskId=g.taskId
                    WHERE (g.barcode=NEW.barcode OR g.captureId=NEW.captureId) AND s.state='SEALED')
                BEGIN SELECT RAISE(ABORT,'sealed independent guard must be preserved'); END""")
        }
    }
}
