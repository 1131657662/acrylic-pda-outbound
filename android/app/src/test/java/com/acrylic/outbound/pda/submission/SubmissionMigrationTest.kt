package com.acrylic.outbound.pda.submission

import android.content.ContentValues
import android.database.DatabaseUtils
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.work.WorkFixture
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class SubmissionMigrationTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun versionTwoUpgradePreservesSavedVoidFailureAndDeviceCounters() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            val invalid = f.scan("bad"); f.processor.corrections.acknowledge(invalid.id)
            val first = f.scan(); f.processor.corrections.voidLast(f.assignment, first.id, "实物未装车")
            f.scan()
            val before = rows(f)
            rebuildVersionTwo(f, before)
            f.db = PdaDatabase.open(f.context)
            assertEquals(before, rows(f))
            assertNull(f.db.submissions().forOrder(f.assignment))
            val frozen = SubmissionFreeze(f.store) { f.now }.freeze(f.assignment, "升级后核对短装")
            assertEquals("3", JSONObject(frozen.payload).getString("captureHighWatermark"))
            assertEquals("1", JSONObject(frozen.payload).getString("expectedCount"))
            f.db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }
    private fun schema() = JSONObject(File(System.getProperty("pda.schemas"), "2.json").readText()).getJSONObject("database")
    private fun rows(f: WorkFixture): Map<String, List<ContentValues>> {
        val entities = schema().getJSONArray("entities")
        return (0 until entities.length()).associate { index ->
            val table = entities.getJSONObject(index).getString("tableName")
            table to f.db.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY rowid").use { cursor ->
                buildList { while (cursor.moveToNext()) add(ContentValues().also { DatabaseUtils.cursorRowToContentValues(cursor, it) }) }
            }
        }
    }
    private fun rebuildVersionTwo(f: WorkFixture, records: Map<String, List<ContentValues>>) {
        f.db.close(); f.context.deleteDatabase("outbound-pda.db")
        f.context.openOrCreateDatabase("outbound-pda.db", 0, null).use { db ->
            val old = schema(); val entities = old.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                records.getValue(table).forEach { db.insertOrThrow(table, null, it) }
            }
            db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT INTO room_master_table VALUES(42,?)", arrayOf(old.getString("identityHash")))
            db.version = 2
        }
    }
}
