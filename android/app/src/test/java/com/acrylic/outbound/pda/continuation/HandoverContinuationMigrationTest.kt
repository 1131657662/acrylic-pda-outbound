package com.acrylic.outbound.pda.continuation

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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class HandoverContinuationMigrationTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun nonemptyFrozenImportSurvivesMigrationFromPreviousSchema() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverImportFixture(folder.root) { source ->
            otherOrderEvent(source)
            source.scan(); source.scan("bad")
        }.use { f ->
            f.install()
            val stage = f.db.handoverStages().get(f.caseId)!!
            val events = f.db.work().events(f.target)
            val work = f.db.work().order(f.target)
            f.db.close()
            downgrade(f)
            f.db = PdaDatabase.open(f.context)
            assertEquals(PDA_DATABASE_VERSION, f.db.openHelper.readableDatabase.version)
            assertEquals(events, f.db.work().events(f.target))
            assertEquals(work, f.db.work().order(f.target))
            assertArrayEquals(stage.recoveryBytes, f.db.handoverStages().get(f.caseId)!!.recoveryBytes)
            assertNull(f.db.handoverContinuations().get(f.caseId))
            assertEquals(work, f.install())
            f.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }

    private fun otherOrderEvent(source: com.acrylic.outbound.pda.work.WorkFixture) {
        val other = java.util.UUID.randomUUID().toString()
        val orderId = java.util.UUID.randomUUID().toString()
        source.db.runInTransaction {
            source.db.preparations().insertOrder(source.store.prepared(source.assignment).copy(assignmentId = other, orderId = orderId))
            source.db.work().insertOrder(checkNotNull(source.db.work().order(source.assignment)).copy(assignmentId = other, orderId = orderId))
            source.store.event(other, null, "WORK_OPENED")
        }
    }

    private fun downgrade(f: HandoverImportFixture) {
        val schema = JSONObject(File(System.getProperty("pda.schemas"), "29.json").readText()).getJSONObject("database")
        val entities = schema.getJSONArray("entities")
        val event = (0 until entities.length()).map { entities.getJSONObject(it) }.single { it.getString("tableName") == "work_event" }
        val fields = event.getJSONArray("fields")
        val columns = (0 until fields.length()).joinToString(",") { fields.getJSONObject(it).getString("columnName") }
        f.context.openOrCreateDatabase("outbound-pda.db", 0, null).use { old ->
            com.acrylic.outbound.pda.temporary.TemporaryContinuationMigrationFixture.restorePreContinuation(old)
            old.execSQL("DROP TABLE local_handover_continuation")
            old.execSQL("CREATE TEMP TABLE event_copy AS SELECT $columns FROM work_event")
            old.execSQL("DROP TABLE work_event")
            old.execSQL(event.getString("createSql").replace("\${TABLE_NAME}", "work_event"))
            old.execSQL("INSERT INTO work_event($columns) SELECT $columns FROM event_copy")
            val indices = event.getJSONArray("indices")
            for (i in 0 until indices.length()) old.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", "work_event"))
            old.execSQL("UPDATE room_master_table SET identity_hash=? WHERE id=42", arrayOf(schema.getString("identityHash")))
            old.version = 29
        }
    }
}
