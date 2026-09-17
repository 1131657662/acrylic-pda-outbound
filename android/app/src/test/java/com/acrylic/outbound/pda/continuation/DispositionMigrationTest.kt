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
            val hash = JSONObject(File(System.getProperty("pda.schemas"), "5.json").readText()).getJSONObject("database").getString("identityHash")
            f.db.openHelper.writableDatabase.apply {
                execSQL("DROP TABLE local_disposition"); execSQL("UPDATE room_master_table SET identity_hash=? WHERE id=42", arrayOf(hash)); version = 5
            }
            f.db.close(); f.db = PdaDatabase.open(f.context)
            assertEquals(frozen.payload, f.db.submissions().get(frozen.id)!!.payload)
            assertEquals(ticket, f.db.continuations().get("migration-continuation")!!.ticketJson)
            assertEquals(facts, f.db.scans().page(f.assignment, 0, 100)); assertEquals(events, f.db.work().events(f.assignment))
            assertTrue(f.db.dispositions().all(f.assignment).isEmpty()); f.store.verifyTotals(f.assignment)
            f.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }
}
