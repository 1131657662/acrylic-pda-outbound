package com.acrylic.outbound.pda.temporary

import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.io.File

/** Restores only this migration's additions in fixtures that simulate an older database in place. */
object TemporaryContinuationMigrationFixture {
    fun restorePreContinuation(db: SQLiteDatabase) {
        db.rawQuery("SELECT COUNT(*) FROM temporary_continuation", null).use { check(it.moveToFirst() && it.getInt(0) == 0) }
        db.rawQuery("SELECT COUNT(*) FROM temporary_draft WHERE workRound<>1 OR authorizationHistoryJson<>'[]'", null)
            .use { check(it.moveToFirst() && it.getInt(0) == 0) }
        val schema = JSONObject(File(System.getProperty("pda.schemas"), "30.json").readText()).getJSONObject("database")
        val entities = schema.getJSONArray("entities")
        val draft = (0 until entities.length()).map { entities.getJSONObject(it) }.single { it.getString("tableName") == "temporary_draft" }
        val fields = draft.getJSONArray("fields")
        val columns = (0 until fields.length()).joinToString(",") { "`${fields.getJSONObject(it).getString("columnName")}`" }
        db.execSQL("DROP TABLE temporary_continuation")
        db.execSQL("CREATE TEMP TABLE pre_continuation_draft AS SELECT $columns FROM temporary_draft")
        db.execSQL("DROP TABLE temporary_draft")
        db.execSQL(draft.getString("createSql").replace("\${TABLE_NAME}", "temporary_draft"))
        db.execSQL("INSERT INTO temporary_draft($columns) SELECT $columns FROM pre_continuation_draft")
        val indices = draft.getJSONArray("indices")
        for (i in 0 until indices.length()) db.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", "temporary_draft"))
        db.execSQL("DROP TABLE pre_continuation_draft")
    }
}
