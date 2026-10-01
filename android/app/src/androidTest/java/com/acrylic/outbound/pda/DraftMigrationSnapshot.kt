package com.acrylic.outbound.pda

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/** Lossless typed rows; JSON framing distinguishes null, empty text, delimiters and binary bytes. */
internal object DraftMigrationSnapshot {
    fun capture(db: SQLiteDatabase): JSONObject {
        val tables = mutableListOf<String>()
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name", null).use {
            while (it.moveToNext()) tables.add(it.getString(0))
        }
        val result = JSONObject()
        for (table in tables.filterNot { it.startsWith("sqlite_") ||
            it in setOf("android_metadata", "room_master_table", "local_source_binding") }) {
            check(table.matches(Regex("[A-Za-z0-9_]+")))
            val rows = mutableListOf<String>()
            db.rawQuery("SELECT * FROM `$table`", null).use {
                while (it.moveToNext()) rows.add(row(it))
            }
            result.put(table, JSONObject().put("count", rows.size).put("rows", JSONArray(rows.sorted())))
        }
        db.rawQuery("PRAGMA foreign_key_check", null).use { check(!it.moveToFirst()) }
        return result
    }

    private fun row(cursor: Cursor): String {
        val values = JSONArray()
        for (index in cursor.columnNames.indices.sortedBy { cursor.columnNames[it] }) {
            val type = cursor.getType(index)
            val value: Any = when (type) {
                Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index).toString()
                Cursor.FIELD_TYPE_FLOAT -> java.lang.Double.toHexString(cursor.getDouble(index))
                Cursor.FIELD_TYPE_BLOB -> Base64.getEncoder().encodeToString(cursor.getBlob(index))
                else -> cursor.getString(index)
            }
            values.put(JSONArray().put(cursor.columnNames[index]).put(type).put(value))
        }
        return values.toString()
    }

    fun assertSame(before: JSONObject, after: JSONObject) {
        check(before.keys().asSequence().toSet() == after.keys().asSequence().toSet())
        for (table in before.keys()) {
            check(before.getJSONObject(table).toString() == after.getJSONObject(table).toString()) {
                "Migration changed typed rows in $table"
            }
        }
        for (table in listOf("local_draft_stop", "local_draft_stop_item", "local_draft_stop_signature",
            "local_draft_transfer", "local_draft_request", "local_draft_receipt", "local_draft_queue")) {
            check(before.getJSONObject(table).getInt("count") > 0) { "Empty required table: $table" }
        }
    }
}
