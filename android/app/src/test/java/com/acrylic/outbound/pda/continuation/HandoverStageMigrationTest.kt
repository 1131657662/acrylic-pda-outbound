package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.data.PDA_DATABASE_VERSION
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.submission.SubmissionFreeze
import com.acrylic.outbound.pda.work.WorkFixture
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
class HandoverStageMigrationTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun existingFrozenWorkSurvivesAdditiveMigration() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            f.scan()
            val frozen = SubmissionFreeze(f.store) { f.now }.freeze(f.assignment, "迁移前冻结")
            val events = f.db.work().events(f.assignment)
            f.db.close()
            val schema = JSONObject(File(System.getProperty("pda.schemas"), "28.json").readText())
                .getJSONObject("database")
            f.context.openOrCreateDatabase("outbound-pda.db", 0, null).use { old ->
                old.execSQL("DROP TABLE local_handover_stage")
                old.execSQL("UPDATE room_master_table SET identity_hash=? WHERE id=42", arrayOf(schema.getString("identityHash")))
                old.version = 28
            }
            f.db = PdaDatabase.open(f.context)
            assertEquals(PDA_DATABASE_VERSION, f.db.openHelper.readableDatabase.version)
            assertEquals(frozen, f.db.submissions().get(frozen.id))
            assertEquals(events, f.db.work().events(f.assignment))
            assertNull(f.db.handoverStages().get("missing"))
            f.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }

    @Test fun largeOriginalBodySurvivesCloseAndReopen() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            val bytes = ByteArray(3 * 1024 * 1024 + 17) { (it % 251).toByte() }
            val row = LocalHandoverStage("case", "origin", "order", "target", "user", "warehouse",
                7, "hash", "{}", "{}", bytes)
            f.db.handoverStages().insert(row)
            f.db.close()
            f.db = PdaDatabase.open(f.context)
            val saved = f.db.handoverStages().get("case")!!
            assertArrayEquals(bytes, saved.recoveryBytes)
            assertEquals(row.copy(recoveryBytes = saved.recoveryBytes), saved)
            assertThrows(Exception::class.java) {
                f.db.handoverStages().insert(row.copy(caseId = "second-case"))
            }
            assertNull(f.db.handoverStages().get("second-case"))
        }
    }
}
