package com.acrylic.outbound.pda

import android.database.Cursor
import org.json.JSONObject
import java.security.MessageDigest

/** Read-only fingerprints; raw identity and business values stay in the existing database. */
internal class DraftDiagnosticDatabaseSnapshot(private val app: PdaApplication) {
    fun read(): JSONObject {
        val db = app.database.openHelper.readableDatabase
        val tables = mutableListOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").use { cursor ->
            while (cursor.moveToNext()) tables += cursor.getString(0)
        }
        return JSONObject().apply {
            tables.filter { it.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) }.forEach { table ->
                val rows = mutableListOf<String>()
                db.query("SELECT * FROM \"$table\"").use { cursor ->
                    while (cursor.moveToNext()) rows += rowHash(cursor)
                }
                put(table, JSONObject().put("count", rows.size).put("sha256", hash(rows.sorted().joinToString("\n").toByteArray())))
            }
        }
    }

    private fun rowHash(cursor: Cursor): String {
        val digest = MessageDigest.getInstance("SHA-256")
        repeat(cursor.columnCount) { index ->
            val type = cursor.getType(index)
            val bytes = when (type) {
                Cursor.FIELD_TYPE_NULL -> byteArrayOf()
                Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index)
                else -> cursor.getString(index).toByteArray(Charsets.UTF_8)
            }
            digest.update("$type:${bytes.size}:".toByteArray())
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
