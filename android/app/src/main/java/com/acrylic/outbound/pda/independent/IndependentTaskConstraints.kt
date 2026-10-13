package com.acrylic.outbound.pda.independent

import androidx.sqlite.db.SupportSQLiteDatabase

/** No local business deletion or rewriting of fixed task/plan/request/audit originals. */
internal object IndependentTaskConstraints {
    fun install(db: SupportSQLiteDatabase) {
        IndependentClosedConstraints.install(db)
        listOf("independent_task", "independent_task_line", "independent_task_request", "independent_task_event").forEach { table ->
            listOf("UPDATE", "DELETE").forEach { action ->
                db.execSQL("""CREATE TRIGGER IF NOT EXISTS immutable_${table}_${action.lowercase()} BEFORE $action ON $table
                    BEGIN SELECT RAISE(ABORT,'independent original is immutable'); END""")
            }
        }
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS immutable_independent_task_state_delete BEFORE DELETE ON independent_task_state
            BEGIN SELECT RAISE(ABORT,'independent state must be preserved'); END""")
    }
}
