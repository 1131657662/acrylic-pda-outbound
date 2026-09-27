package com.acrylic.outbound.pda.temporary
import com.acrylic.outbound.pda.data.PDA_DATABASE_VERSION

import android.content.ContentValues
import android.database.DatabaseUtils
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.submission.SubmissionFreeze
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
class TemporarySubmissionMigrationTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun roomSixteenUpgradePreservesFormalFreezeTemporaryScansAndBothGuards() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { formal ->
            formal.scan(); val frozen = SubmissionFreeze(formal.store) { formal.now }.freeze(formal.assignment, "迁移保留")
            TemporaryWorkFixture(folder.newFolder(), formal.db).use { it.start(); it.scan() }
            val before = rows(formal); rebuild(formal, before); formal.db = PdaDatabase.open(formal.context)
            assertEquals(PDA_DATABASE_VERSION, formal.db.openHelper.readableDatabase.version)
            assertTrue(before.size > 25); assertEquals(normalize(before), normalize(rows(formal)))
            formal.db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM temporary_guard").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            assertEquals(frozen, formal.db.submissions().get(frozen.id)); assertNotNull(formal.db.scans().guard(formal.barcodes[0]))
            TemporaryFixture(folder.newFolder(), formal.db).use { f ->
                val request = checkNotNull(f.repository.local()); val catalog = TemporaryCatalog.load(f.db, request)
                val store = TemporaryWorkStore(f.db, f) { com.acrylic.outbound.pda.security.TimeSample(1000, f.time.toEpochMilli(), 1) }
                val draft = TemporaryDraftRepository(store).create(java.util.UUID.randomUUID().toString())
                assertEquals(catalog.preparation.authorizationId, draft.authorizationId)
            }
            formal.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }
    private fun schema() = JSONObject(File(System.getProperty("pda.schemas"), "16.json").readText()).getJSONObject("database")
    private fun rows(f: WorkFixture): Map<String, List<ContentValues>> {
        val entities = schema().getJSONArray("entities")
        return (0 until entities.length()).associate { index ->
            val entity = entities.getJSONObject(index); val table = entity.getString("tableName"); val fields = entity.getJSONArray("fields")
            val columns = (0 until fields.length()).joinToString(",") { "`${fields.getJSONObject(it).getString("columnName")}`" }
            table to f.db.openHelper.readableDatabase.query("SELECT $columns FROM `$table` ORDER BY rowid").use { cursor ->
                buildList { while (cursor.moveToNext()) add(ContentValues().also { DatabaseUtils.cursorRowToContentValues(cursor, it) }) }
            }
        }
    }
    private fun normalize(rows: Map<String, List<ContentValues>>) = rows.mapValues { (_, list) -> list.map { row ->
        row.valueSet().associate { (key, value) -> key to if (value is ByteArray) value.toList() else value }
    } }
    private fun rebuild(f: WorkFixture, rows: Map<String, List<ContentValues>>) {
        f.db.close(); f.context.deleteDatabase("outbound-pda.db")
        f.context.openOrCreateDatabase("outbound-pda.db", 0, null).use { db ->
            val old = schema(); val entities = old.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index); val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (i in 0 until indices.length()) db.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
                rows.getValue(table).forEach { db.insertOrThrow(table, null, it) }
            }
            db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT INTO room_master_table VALUES(42,?)", arrayOf(old.getString("identityHash"))); db.version = 16
        }
    }
}
