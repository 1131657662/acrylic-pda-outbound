package com.acrylic.outbound.pda.continuation

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
class DispositionMigrationTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun versionFiveUpgradePreservesFactsAndLargeContinuationTicket() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            f.scan(); val frozen = SubmissionFreeze(f.store) { f.now }.freeze(f.assignment, "迁移前冻结")
            val ticket = "核验凭据".repeat(450000)
            f.db.continuations().insert(LocalContinuation("migration-continuation", f.assignment, f.input.assignment.toString(), ticket, 2, 2, "old-recall"))
            val facts = f.db.scans().page(f.assignment, 0, 100); val events = f.db.work().events(f.assignment)
            rebuildFive(f); f.db = PdaDatabase.open(f.context)
            assertEquals(frozen.payload, f.db.submissions().get(frozen.id)!!.payload)
            assertEquals(ticket, f.db.continuations().get("migration-continuation")!!.ticketJson)
            assertEquals(facts, f.db.scans().page(f.assignment, 0, 100)); assertEquals(events, f.db.work().events(f.assignment))
            assertTrue(f.db.dispositions().all(f.assignment).isEmpty()); f.store.verifyTotals(f.assignment)
            f.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }
    private fun rebuildFive(f: WorkFixture) {
        f.db.close()
        val schema = JSONObject(File(System.getProperty("pda.schemas"), "5.json").readText()).getJSONObject("database")
        val source = f.context.getDatabasePath("outbound-pda.db")
        f.context.deleteDatabase("migration-five.db")
        f.context.openOrCreateDatabase("migration-five.db", 0, null).use { old ->
            old.execSQL("ATTACH DATABASE ? AS original", arrayOf(source.absolutePath))
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index); val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (i in 0 until indices.length()) old.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
                val fields = entity.getJSONArray("fields")
                val columns = (0 until fields.length()).joinToString(",") { "`${fields.getJSONObject(it).getString("columnName")}`" }
                old.execSQL("INSERT INTO `$table` ($columns) SELECT $columns FROM original.`$table`")
            }
            old.execSQL("DETACH DATABASE original")
            old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            old.execSQL("INSERT INTO room_master_table VALUES(42,?)", arrayOf(schema.getString("identityHash"))); old.version = 5
        }
        f.context.deleteDatabase("outbound-pda.db")
        f.context.getDatabasePath("migration-five.db").copyTo(source)
        f.context.deleteDatabase("migration-five.db")
    }
}
