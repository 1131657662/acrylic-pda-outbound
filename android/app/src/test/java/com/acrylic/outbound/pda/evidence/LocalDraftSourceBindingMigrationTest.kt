package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.data.PDA_DATABASE_VERSION
import com.acrylic.outbound.pda.sync.SyncRun
import com.acrylic.outbound.pda.temporary.TemporaryWorkFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File

/**
 * R06 A1.1 criterion ④ — "非空迁移保留原键/请求/回执", covering the **27 → 28** segment
 * (`LocalSourceBindingMigration`), which is the only version step in the v1..v28 chain that still sits inside the
 * LocalDraft era and had no non-empty test: 6→7, 7→8, 9→10, 10→11, 11→12 and 17→18 are outside this era (no
 * local-draft keys/requests/receipts exist yet), and every 22→27 step already has its own non-empty test.
 *
 * The test builds real stop evidence, transfer plan, requests and a durable receipt on the current schema, forces
 * the database back to 27 (remove later tables/columns, restore its schema and Room identity hash), reopens so
 * Room runs the real migration, and then compares every preserved fact item by item.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LocalDraftSourceBindingMigrationTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun nonemptyRoomTwentySevenRetainsOriginalKeysRequestsReceiptsAndDoesNotInventBindings() =
        runBlocking<Unit>(Dispatchers.IO) {
            TemporaryWorkFixture(folder.root).use { f ->
                f.start(); f.receive()
                val stop = LocalDraftStopRepository(f.store).stop(f.id)
                val wire = LocalDraftRecoveryFixture(f)
                val plan = wire.repository().prepare(f.id)
                wire.repository().resume(f.id, SyncRun(batches = 1))
                val requests = wire.all()
                val receipt = f.db.localDraftTransfers().receipt(requests.first().originalKey)!!
                val signature = f.db.localDraftStopSignatures().get(f.id)
                val events = f.db.temporaryScans().events(f.id)
                // Freeze automatic transfer on purpose: migrating must preserve the pause, not resurrect it.
                val queued = f.db.localDraftQueue().get(f.id)!!
                f.db.localDraftQueue().update(queued.copy(state = "ACTION_REQUIRED", autoRetry = false,
                    error = "paused by test"))
                val signerCallsBefore = f.fixture.stopSigner.calls
                val postsBefore = wire.posts.toList()

                downgradeTo27(f)
                // A retrospective re-signing would be a migration bug; make it fail loudly instead of quietly pass.
                f.fixture.stopSigner.unavailable = true
                f.reopen()

                assertEquals(PDA_DATABASE_VERSION, f.db.openHelper.readableDatabase.version)

                // original keys / requests / receipts / stop evidence survive the step, item by item
                assertEquals(stop, LocalDraftStopRepository(f.store).read(f.id))
                assertEquals(signature, f.db.localDraftStopSignatures().get(f.id))
                assertEquals(plan, LocalDraftTransferRepository(f.store).read(f.id))
                assertEquals(requests, wire.all())
                val restored = f.db.localDraftTransfers().receipt(receipt.originalKey)!!
                assertEquals(receipt.state, restored.state)
                assertEquals(receipt.signingKeyJson, restored.signingKeyJson)
                assertArrayEquals(receipt.responseBytes, restored.responseBytes)
                assertEquals(events, f.db.temporaryScans().events(f.id))

                // A migration must neither drop the queue row nor resurrect automatic transfer: the deliberate
                // pause survives the step, and migrating never enqueues a historical stopped draft.
                val after = f.db.localDraftQueue().get(f.id)!!
                assertEquals("ACTION_REQUIRED", after.state)
                assertFalse("a migration must not re-enable automatic transfer", after.autoRetry)
                assertEquals("paused by test", after.error)
                assertEquals(queued.totalRequests, after.totalRequests)
                assertEquals(postsBefore, wire.posts)
                // and it must not re-sign: the private key is unavailable, so retrospective signing would fail
                assertEquals(signerCallsBefore, f.fixture.stopSigner.calls)

                // the table this step adds is created empty: a migration must not fabricate a source binding
                f.db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM local_source_binding").use {
                    it.moveToFirst(); assertEquals(0, it.getInt(0))
                }
                f.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            }
        }

    /** Remove all later additions and restore the exported historical event schema before migration. */
    private fun downgradeTo27(f: TemporaryWorkFixture) {
        f.db.close()
        val schema = JSONObject(File(checkNotNull(System.getProperty("pda.schemas")), "27.json").readText())
            .getJSONObject("database")
        f.fixture.context.openOrCreateDatabase("outbound-pda.db", 0, null).use { db ->
            com.acrylic.outbound.pda.temporary.TemporaryContinuationMigrationFixture.restorePreContinuation(db)
            db.execSQL("DROP TABLE local_handover_continuation")
            db.execSQL("DROP TABLE local_handover_stage")
            db.execSQL("DROP TABLE local_source_binding")
            restoreEvents(db, schema)
            db.execSQL("UPDATE room_master_table SET identity_hash=? WHERE id=42", arrayOf(schema.getString("identityHash")))
            db.version = 27
        }
    }

    private fun restoreEvents(db: android.database.sqlite.SQLiteDatabase, schema: JSONObject) {
        val entities = schema.getJSONArray("entities")
        val event = (0 until entities.length()).map { entities.getJSONObject(it) }.single { it.getString("tableName") == "work_event" }
        val fields = event.getJSONArray("fields")
        val columns = (0 until fields.length()).joinToString(",") { fields.getJSONObject(it).getString("columnName") }
        db.execSQL("CREATE TEMP TABLE old_events AS SELECT $columns FROM work_event")
        db.execSQL("DROP TABLE work_event")
        db.execSQL(event.getString("createSql").replace("\${TABLE_NAME}", "work_event"))
        db.execSQL("INSERT INTO work_event($columns) SELECT $columns FROM old_events")
        val indices = event.getJSONArray("indices")
        for (i in 0 until indices.length()) db.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", "work_event"))
        db.rawQuery("PRAGMA table_info(work_event)", null).use { rows ->
            while (rows.moveToNext()) assertFalse(rows.getString(1) == "orderPosition")
        }
    }
}
