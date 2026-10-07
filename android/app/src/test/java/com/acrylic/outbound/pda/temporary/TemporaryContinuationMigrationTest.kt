package com.acrylic.outbound.pda.temporary

import android.content.ContentValues
import android.database.DatabaseUtils
import com.acrylic.outbound.pda.data.PDA_DATABASE_VERSION
import com.acrylic.outbound.pda.data.PdaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryContinuationMigrationTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun nonemptyStoppedSourceSurvivesUpgradeBeforeContinuationConsumption() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val before = rows(f.db); val original = f.draft
            assertTrue(before.getValue("local_e2a_object").isNotEmpty())
            assertTrue(before.getValue("temporary_part_chunk").isNotEmpty())
            rebuild(f, before)
            assertEquals(PDA_DATABASE_VERSION, f.db.openHelper.readableDatabase.version)
            assertEquals(normalize(before), normalize(rows(f.db))); assertEquals(original, f.draft)
            assertEquals(1L, f.draft.workRound); assertEquals("[]", f.draft.authorizationHistoryJson)
            assertNull(f.db.temporaryContinuations().forSource(f.work.id))
            val saved = f.activate(); f.work.reopen()
            assertEquals(saved, f.activate()); assertEquals(original, f.draft)
            f.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            f.db.openHelper.readableDatabase.query("PRAGMA synchronous").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
        }
    }

    @Test fun consumedTicketAndOriginalAckSurviveQueueMigration() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val next = f.activate(); val before = rows(f.db, 31)
            rebuild(f, before, 31)
            assertEquals(normalize(before), normalize(rows(f.db, 31)))
            assertEquals(next, f.db.temporaryDrafts().get(next.clientOrderId))
            assertEquals(1L, f.db.localE2aStops().get(f.work.id)!!.workRound)
            assertTrue(f.db.temporaryContinuationQueue().pending().isEmpty())
            assertEquals(next, f.activate())
            f.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }

    private fun schema(version: Int = 30) = JSONObject(File(System.getProperty("pda.schemas"), "$version.json").readText()).getJSONObject("database")

    private fun rows(db: PdaDatabase, version: Int = 30): Map<String, List<ContentValues>> {
        val entities = schema(version).getJSONArray("entities")
        return (0 until entities.length()).associate { index ->
            val entity = entities.getJSONObject(index); val table = entity.getString("tableName"); val fields = entity.getJSONArray("fields")
            val columns = (0 until fields.length()).joinToString(",") { "`${fields.getJSONObject(it).getString("columnName")}`" }
            table to db.openHelper.readableDatabase.query("SELECT $columns FROM `$table` ORDER BY rowid").use { cursor ->
                buildList { while (cursor.moveToNext()) add(ContentValues().also { DatabaseUtils.cursorRowToContentValues(cursor, it) }) }
            }
        }
    }

    private fun normalize(rows: Map<String, List<ContentValues>>) = rows.mapValues { (_, records) -> records.map { row ->
        row.valueSet().associate { (key, value) -> key to if (value is ByteArray) value.toList() else value }
    } }

    private fun rebuild(f: TemporaryContinuationFixture, records: Map<String, List<ContentValues>>, version: Int = 30) {
        val fixture = f.work.fixture; fixture.db.close(); fixture.context.deleteDatabase("outbound-pda.db")
        fixture.context.openOrCreateDatabase("outbound-pda.db", 0, null).use { db ->
            val old = schema(version); val entities = old.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (j in 0 until indices.length())
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                records.getValue(table).forEach { db.insertOrThrow(table, null, it) }
            }
            db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT INTO room_master_table VALUES(42,?)", arrayOf(old.getString("identityHash"))); db.version = version
        }
        fixture.db = PdaDatabase.open(fixture.context); f.work.store = f.work.newStore(); f.work.store.load(f.work.id)
    }
}
