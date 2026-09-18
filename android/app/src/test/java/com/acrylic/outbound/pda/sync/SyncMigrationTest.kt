package com.acrylic.outbound.pda.sync

import com.acrylic.outbound.pda.recall.LocalRecall

import com.acrylic.outbound.pda.submission.SubmissionFreeze

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
class SyncMigrationTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun versionEightUpgradePreservesAllFactsGuardsFrozenBodyAndAuthorizationHistory() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            val invalid = f.scan("bad"); f.processor.corrections.acknowledge(invalid.id)
            val first = f.scan(); f.processor.corrections.voidLast(f.assignment, first.id, "实物未装车")
            f.scan()
            val frozenBefore = SubmissionFreeze(f.store) { f.now }.freeze(f.assignment, "升级前短装冻结")
            f.db.recalls().insert(LocalRecall("migration-recall", f.assignment, "{}", "migration-package", "x".repeat(1500000), "0".repeat(64), "RECALLED", "proof"))
            val before = rows(f)
            rebuildVersionEight(f, before)
            f.db = PdaDatabase.open(f.context)
            assertEquals(before, rows(f)); assertNull(f.db.sync().state("1", "warehouse")); assertNull(f.db.sync().recovery())
            assertEquals(1500000, f.db.recalls().get(f.assignment)!!.payload!!.length)
            assertEquals(f.assignment, f.db.captures().page(f.assignment, 0, 10).first().context.originAssignmentId)
            f.db.runInTransaction { f.db.recalls().supersede(f.assignment); f.db.submissions().supersede(f.assignment) }
            assertNull(f.db.recalls().header(f.assignment)); assertNull(f.db.submissions().forOrder(f.assignment))
            assertEquals(frozenBefore.payload, f.db.submissions().get(frozenBefore.id)!!.payload)
            f.db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }

        }
    }
    private fun schema() = JSONObject(File(System.getProperty("pda.schemas"), "8.json").readText()).getJSONObject("database")
    private fun rows(f: WorkFixture): Map<String, List<ContentValues>> {
        val entities = schema().getJSONArray("entities")
        return (0 until entities.length()).associate { index ->
            val table = entities.getJSONObject(index).getString("tableName")
            val fields = entities.getJSONObject(index).getJSONArray("fields")
            val columns = (0 until fields.length()).joinToString(",") { fields.getJSONObject(it).getString("columnName") }
            table to f.db.openHelper.readableDatabase.query("SELECT $columns FROM $table ORDER BY rowid").use { cursor ->
                buildList { while (cursor.moveToNext()) add(ContentValues().also { DatabaseUtils.cursorRowToContentValues(cursor, it) }) }
            }
        }
    }
    private fun rebuildVersionEight(f: WorkFixture, records: Map<String, List<ContentValues>>) {
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
            db.version = 8
        }
    }
}
