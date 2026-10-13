package com.acrylic.outbound.pda.independent

import androidx.sqlite.db.SupportSQLiteDatabase

/** Sealed collections preserve processing, audit and effective deduplication facts as well as originals. */
internal object IndependentClosedConstraints {
    fun install(db: SupportSQLiteDatabase) {
        originals(db); processing(db); guards(db)
        IndependentClosedReplacementConstraints.install(db)
    }
    private fun originals(db: SupportSQLiteDatabase) {
        listOf("independent_task", "independent_task_request").forEach { table ->
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS ${table}_closed_identity_insert BEFORE INSERT ON $table
                WHEN EXISTS(SELECT 1 FROM independent_task_state WHERE taskId=NEW.id AND state='SEALED')
                BEGIN SELECT RAISE(ABORT,'sealed independent original must be preserved'); END""")
        }
        listOf("independent_capture", "independent_scan", "independent_void", "independent_guard", "independent_task_event",
            "independent_task_line", "independent_task_state").forEach { table ->
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS ${table}_closed_insert BEFORE INSERT ON $table
                WHEN EXISTS(SELECT 1 FROM independent_task_state WHERE taskId=NEW.taskId AND state='SEALED')
                BEGIN SELECT RAISE(ABORT,'independent task is sealed'); END""")
        }
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS independent_task_state_closed_update BEFORE UPDATE ON independent_task_state
            WHEN OLD.state='SEALED' BEGIN SELECT RAISE(ABORT,'independent task is sealed'); END""")
    }
    private fun processing(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS independent_capture_state_closed_update BEFORE UPDATE ON independent_capture_state
            WHEN EXISTS(SELECT 1 FROM independent_capture c INNER JOIN independent_task_state s ON s.taskId=c.taskId
                WHERE c.id=OLD.captureId AND s.state='SEALED') BEGIN SELECT RAISE(ABORT,'independent task is sealed'); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS independent_capture_state_closed_insert BEFORE INSERT ON independent_capture_state
            WHEN EXISTS(SELECT 1 FROM independent_capture c INNER JOIN independent_task_state s ON s.taskId=c.taskId
                WHERE c.id=NEW.captureId AND s.state='SEALED') BEGIN SELECT RAISE(ABORT,'independent task is sealed'); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS independent_capture_state_closed_target BEFORE UPDATE ON independent_capture_state
            WHEN EXISTS(SELECT 1 FROM independent_capture c INNER JOIN independent_task_state s ON s.taskId=c.taskId
                WHERE c.id=NEW.captureId AND s.state='SEALED') BEGIN SELECT RAISE(ABORT,'independent task is sealed'); END""")
    }
    private fun guards(db: SupportSQLiteDatabase) {
        listOf("UPDATE", "DELETE").forEach { action ->
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS independent_guard_closed_${action.lowercase()} BEFORE $action ON independent_guard
                WHEN EXISTS(SELECT 1 FROM independent_task_state WHERE taskId=OLD.taskId AND state='SEALED')
                BEGIN SELECT RAISE(ABORT,'sealed independent guard must be preserved'); END""")
        }
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS independent_guard_closed_target BEFORE UPDATE ON independent_guard
            WHEN EXISTS(SELECT 1 FROM independent_task_state WHERE taskId=NEW.taskId AND state='SEALED')
            BEGIN SELECT RAISE(ABORT,'sealed independent guard must be preserved'); END""")
    }
}
