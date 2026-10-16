package com.acrylic.outbound.pda.temporary

import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.data.PdaDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64

/** File-backed product Room databases, retained outside Robolectric's disposable context. */
internal object ArchiveRestoreRoom {
    fun open(directory: File): PdaDatabase {
        check(directory.isDirectory)
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getDatabasePath(name: String) = File(directory, name)
        }
        return PdaDatabase.open(context)
    }

    fun snapshot(db: PdaDatabase): JSONObject {
        val result = JSONObject()
        val sql = db.openHelper.readableDatabase
        sql.query("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").use { names ->
            while (names.moveToNext()) {
                val name = names.getString(0); check(name.matches(Regex("[A-Za-z0-9_]+")))
                val rows = mutableListOf<String>()
                sql.query("SELECT * FROM `$name`").use { cursor ->
                    while (cursor.moveToNext()) rows.add(row(cursor).toString())
                }
                result.put(name, JSONArray(rows.sorted()))
            }
        }
        return result
    }

    private fun row(cursor: Cursor) = JSONObject().also { result ->
        for (index in 0 until cursor.columnCount) result.put(cursor.getColumnName(index), when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
            Cursor.FIELD_TYPE_BLOB -> JSONObject().put("blobBase64", Base64.getEncoder().encodeToString(cursor.getBlob(index)))
            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index).toString()
            else -> cursor.getString(index)
        })
    }

    fun copyOriginal(source: File, target: File) {
        check(!target.exists()); check(target.mkdirs())
        source.listFiles()!!.forEach { file ->
            check(file.isFile); file.copyTo(File(target, file.name), overwrite = false)
        }
    }

    fun pendingSnapshot(db: PdaDatabase, attempt: File) {
        check(!db.inTransaction())
        val before = snapshot(db)
        val directory = File(attempt, "android-pending-room"); check(directory.mkdirs())
        val target = File(directory, "outbound-pda.db"); check(!target.exists())
        db.openHelper.writableDatabase.execSQL("VACUUM INTO ?", arrayOf(target.absolutePath))
        check(target.isFile && target.length() > 0)
        check(com.acrylic.outbound.pda.submission.SubmissionJson.canonical(before) ==
            com.acrylic.outbound.pda.submission.SubmissionJson.canonical(snapshot(db)))
        File(attempt, "android-pending-facts.json").writeText(before.toString())
    }
}
