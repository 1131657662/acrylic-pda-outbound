package com.acrylic.outbound.pda.ui

import com.acrylic.outbound.pda.rescue.RescueBundle
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/** SQLite values retain storage types before deterministic sorting. */
internal object TemporaryPagesRoomDigest {
    fun read(f: TemporaryPagesExportFixture): String = runBlocking(Dispatchers.IO) {
        val db = f.base.app.database.openHelper.readableDatabase
        val tables = db.query("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        val content = JSONArray()
        tables.forEach { table ->
            content.put(JSONObject().put("table", table).put("rows", JSONArray(tableRows(db, table))))
        }
        RescueBundle.sha(content.toString().toByteArray(Charsets.UTF_8)).also {
            println("temporary-pages Room tables=${tables.size} typed-content-sha256=$it")
        }
    }

    private fun tableRows(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): List<String> =
        db.query("SELECT * FROM `$table`").use { cursor ->
            buildList { while (cursor.moveToNext()) add(canonicalRow(cursor)) }.sorted()
        }

    private fun canonicalRow(cursor: android.database.Cursor): String = JSONArray().apply {
        repeat(cursor.columnCount) { column ->
            val type = cursor.getType(column)
            val value = when (type) {
                android.database.Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(column)
                android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(column)
                android.database.Cursor.FIELD_TYPE_STRING -> cursor.getString(column)
                android.database.Cursor.FIELD_TYPE_BLOB -> Base64.getEncoder().encodeToString(cursor.getBlob(column))
                else -> error("unsupported SQLite value type")
            }
            put(JSONArray().put(type).put(value))
        }
    }.toString()
}
