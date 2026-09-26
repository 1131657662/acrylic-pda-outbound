package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.temporary.TemporaryFreeze
import com.acrylic.outbound.pda.temporary.TemporaryWorkFixture
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
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LocalE2aTransferTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun exactPagesRawMetadataAndKeysSurviveRestart() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.scan()
            f.db.runInTransaction {
                val draft = checkNotNull(f.db.temporaryDrafts().get(f.id))
                repeat(140) { f.store.event(draft, null, "CHECK", "test evidence") }
            }
            TemporaryFreeze(f.store).freeze(f.id, "短装待核对")
            val plan = LocalE2aTransferRepository(f.store).record(f.id)
            val requests = requests(f, plan)
            assertEquals(2, plan.pageCount)
            val stop = checkNotNull(f.db.localE2aStops().get(f.id))
            val pages = (1..plan.pageCount).map { number ->
                val body = JSONObject(checkNotNull(f.db.localE2aTransfers().request(f.id, "PAGE", number.toLong())).requestJson)
                E2aWire.Page(com.acrylic.outbound.pda.submission.SubmissionJson.canonical(body.getJSONObject("pageCore")),
                    body.getString("pageHash"))
            }
            assertEquals(plan.manifestJson, E2aWire.manifest(E2aTransferRequests.identity(stop), pages).canonicalCore)
            f.reopen()
            assertEquals(plan, LocalE2aTransferRepository(f.store).record(f.id))
            assertEquals(requests, requests(f, plan))
            assertEquals(1, f.fixture.stopSigner.calls)
        }
    }

    @Test fun missingPageCannotRegenerateAnOriginalKey() = runBlocking<Unit>(Dispatchers.IO) {
        frozen { f ->
            f.db.openHelper.writableDatabase.execSQL("DELETE FROM local_e2a_request WHERE operation='PAGE'")
            assertThrows(IllegalStateException::class.java) { LocalE2aTransferRepository(f.store).record(f.id) }
            assertNull(f.db.localE2aTransfers().request(f.id, "PAGE", 1))
        }
    }

    @Test fun rewrittenRawMetadataAndDigestCannotEscapeFrozenReferences() = runBlocking<Unit>(Dispatchers.IO) {
        frozen { f ->
            val dao = f.db.localE2aTransfers()
            val row = checkNotNull(dao.request(f.id, "RAW", 1))
            val changed = E2aTransferRequests.saved(f.id, "RAW", 1, row.originalKey,
                JSONObject(row.requestJson).put("bytesSha256", "f".repeat(64)))
            f.db.openHelper.writableDatabase.execSQL(
                "UPDATE local_e2a_request SET requestJson=?,requestSha256=? WHERE operation='RAW' AND ordinal=1",
                arrayOf(changed.requestJson, changed.requestSha256))
            assertThrows(IllegalStateException::class.java) { LocalE2aTransferRepository(f.store).record(f.id) }
        }
    }

    @Test fun changedPageOrStopKeyFailsClosed() = runBlocking<Unit>(Dispatchers.IO) {
        for (sql in listOf("UPDATE local_e2a_request SET originalKey=? WHERE operation='PAGE'",
            "UPDATE local_e2a_transfer SET stopOriginalKey=?")) {
            frozen { f ->
                f.db.openHelper.writableDatabase.execSQL(sql, arrayOf(UUID.randomUUID().toString()))
                assertThrows(IllegalStateException::class.java) { LocalE2aTransferRepository(f.store).record(f.id) }
            }
        }
    }

    @Test fun failedRequestPersistenceRollsBackTheEntireFreeze() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.scan()
            val before = checkNotNull(f.db.work().device(f.catalog.request.deviceId))
            f.db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER fail_request BEFORE INSERT ON local_e2a_request
                BEGIN SELECT RAISE(ABORT,'test durable write failure'); END""")
            assertThrows(android.database.sqlite.SQLiteException::class.java) {
                TemporaryFreeze(f.store).freeze(f.id, "短装待核对")
            }
            assertEquals(before, f.db.work().device(f.catalog.request.deviceId))
            assertEquals("SCANNING", f.db.temporaryDrafts().get(f.id)?.state)
            assertNull(f.db.temporarySubmissions().forOrder(f.id))
            assertNull(f.db.localE2aStops().get(f.id))
            assertNull(f.db.localE2aTransfers().get(f.id))
            assertEquals(0L, f.db.localE2aTransfers().requestCount(f.id))
        }
    }

    @Test fun lostTransferCannotBeRecreatedFromExistingSnapshot() = runBlocking<Unit>(Dispatchers.IO) {
        frozen { f ->
            f.db.openHelper.writableDatabase.execSQL("DELETE FROM local_e2a_request")
            f.db.openHelper.writableDatabase.execSQL("DELETE FROM local_e2a_transfer")
            f.reopen()
            assertThrows(IllegalStateException::class.java) { LocalE2aTransferRepository(f.store).record(f.id) }
            assertEquals(0L, f.db.localE2aTransfers().requestCount(f.id))
        }
    }

    private fun frozen(action: (TemporaryWorkFixture) -> Unit) {
        TemporaryWorkFixture(folder.newFolder()).use { f ->
            f.start(); f.scan(); TemporaryFreeze(f.store).freeze(f.id, "短装待核对")
            action(f)
        }
    }

    private fun requests(f: TemporaryWorkFixture, plan: LocalE2aTransfer): List<LocalE2aRequest> = buildList {
        add(checkNotNull(f.db.localE2aTransfers().request(f.id, "OPEN", 0)))
        add(checkNotNull(f.db.localE2aTransfers().request(f.id, "STOP", 0)))
        add(checkNotNull(f.db.localE2aTransfers().request(f.id, "VERIFY", 0)))
        for (ordinal in 1..plan.rawCount) add(checkNotNull(f.db.localE2aTransfers().request(f.id, "RAW", ordinal)))
        for (ordinal in 1..plan.pageCount) add(checkNotNull(f.db.localE2aTransfers().request(f.id, "PAGE", ordinal.toLong())))
    }
}
