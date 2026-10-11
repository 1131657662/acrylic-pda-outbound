package com.acrylic.outbound.pda.rescue

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject

/** Call sites supply fixed SELECT lists; identifiers never come from a UI or archive. */
internal class RescueRows(private val db: SupportSQLiteDatabase) {
    private var bytes = 0L
    fun read(sql: String, id: String): List<JSONObject> = db.query(sql, arrayOf(id)).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                check(size < 100_000) { "本单原记录超过应急包上限，请保留原库" }
                val row = row(cursor)
                reserve(row.toString())
                add(row)
            }
        }
    }
    fun reserve(text: String) {
        reserveBytes(text.toByteArray(Charsets.UTF_8).size.toLong())
    }
    fun originalBytes(sql: String, id: String) {
        db.query(sql, arrayOf(id)).use { cursor ->
            if (cursor.moveToFirst()) reserveBytes(cursor.getLong(0))
        }
    }
    private fun reserveBytes(size: Long) {
        check(size >= 0)
        bytes = Math.addExact(bytes, size)
        check(bytes <= RescueEnvelope.MAX_BYTES) { "累计原件超过应急读取上限，请保留原库" }
    }
    private fun row(cursor: Cursor): JSONObject = JSONObject().apply {
        for (column in 0 until cursor.columnCount) {
            check(cursor.getType(column) != Cursor.FIELD_TYPE_FLOAT && cursor.getType(column) != Cursor.FIELD_TYPE_BLOB)
            put(cursor.getColumnName(column), if (cursor.isNull(column)) JSONObject.NULL else cursor.getString(column))
        }
    }
}
