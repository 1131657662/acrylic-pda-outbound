package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.submission.SubmissionJson
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

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryContinuationWorkTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun defaultBuildCannotStartEvenAfterVerifiedDownload() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val next = acknowledged(f)
            assertThrows(Exception::class.java) { TemporaryContinuationWork(f.work.store).start(next.clientOrderId) }
            assertEquals("CONTINUATION_BLOCKED", f.db.temporaryDrafts().get(next.clientOrderId)!!.state)
            assertFalse(next.canScan())
        }
    }

    @Test fun missingDownloadConfirmationCannotOpenIsolatedWork() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val next = f.activate(); val store = development(f)
            assertThrows(Exception::class.java) { TemporaryContinuationWork(store).start(next.clientOrderId) }
            assertEquals(next, f.db.temporaryDrafts().get(next.clientOrderId))
            assertEquals(1, f.db.temporaryScans().events(next.clientOrderId).size)
        }
    }

    @Test fun newScanFreezesExactPrefixesAndContinuousSequence() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val barcode = f.work.barcodes[1]; val next = acknowledged(f); val store = development(f)
            TemporaryContinuationWork(store).start(next.clientOrderId)
            assertEquals(1L, store.total(next.clientOrderId).count)
            val processor = TemporaryProcessor(store) {}
            val capture = processor.reception.receive(TemporaryInput(next.clientOrderId, barcode, "BROADCAST", store.sample()))
            processor.drain(next.clientOrderId)
            assertEquals(2L, capture.orderScanSeq)
            assertEquals("COMMITTED", f.db.temporaryScans().capture(capture.id)!!.state)
            val frozen = TemporaryFreeze(store).freeze(next.clientOrderId, "合成续作测试")
            val body = JSONObject(frozen.payload); val old = JSONObject(f.frozen.payload)
            for (field in listOf("captures", "scans", "events")) {
                val prefix = old.getJSONArray(field); val cumulative = body.getJSONArray(field)
                for (i in 0 until prefix.length()) assertEquals(SubmissionJson.canonical(prefix.getJSONObject(i)),
                    SubmissionJson.canonical(cumulative.getJSONObject(i)))
            }
            assertEquals("2", body.getString("workRound")); assertEquals("2", body.getString("captureHighWatermark"))
            assertEquals("2", body.getString("expectedCount")); assertEquals("2", body.getJSONObject("captureSummary").getString("saved"))
            assertEquals(f.frozen.id, body.getJSONObject("temporaryContinuation").getString("predecessorSubmissionId"))
            assertEquals(f.frozen, f.db.temporarySubmissions().get(f.frozen.id))
            val stop = checkNotNull(f.db.localE2aStops().get(next.clientOrderId))
            assertEquals(2L, stop.workRound)
            val open = checkNotNull(f.db.localE2aTransfers().request(next.clientOrderId, "OPEN", 0))
            assertEquals(2L, JSONObject(open.requestJson).getLong("workRound"))
        }
    }

    @Test fun retainedOnlyFreezeDoesNotRequireNewInput() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val next = acknowledged(f); val store = development(f)
            TemporaryContinuationWork(store).start(next.clientOrderId)
            val body = JSONObject(TemporaryFreeze(store).freeze(next.clientOrderId, "保留包续作").payload)
            assertEquals("1", body.getString("expectedCount")); assertEquals("1", body.getString("captureHighWatermark"))
            assertEquals(1, body.getJSONArray("scans").length())
            assertTrue(f.db.temporaryScans().allScans(next.clientOrderId).isEmpty())
        }
    }

    @Test fun retainedBarcodeCannotBeSavedAgain() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val barcode = f.work.barcodes[0]; val next = acknowledged(f); val store = development(f)
            TemporaryContinuationWork(store).start(next.clientOrderId)
            val processor = TemporaryProcessor(store) {}
            val capture = processor.reception.receive(TemporaryInput(next.clientOrderId, barcode, "BROADCAST", store.sample()))
            processor.drain(next.clientOrderId)
            assertEquals("INVALID", f.db.temporaryScans().capture(capture.id)!!.state)
            assertNull(f.db.temporaryScans().scan(capture.id)); assertEquals(1L, store.total(next.clientOrderId).count)
        }
    }

    @Test fun restartReplaysStartedWorkWithoutAnotherStartEvent() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val next = acknowledged(f); val started = TemporaryContinuationWork(development(f)).start(next.clientOrderId)
            f.work.reopen()
            val store = development(f)
            assertEquals(started, TemporaryContinuationWork(store).start(next.clientOrderId))
            assertEquals(1, f.db.temporaryScans().events(next.clientOrderId).count { it.type == "TEMP_CONTINUATION_WORK_STARTED" })
            val closedStore = f.work.newStore(); closedStore.load(next.clientOrderId)
            assertThrows(Exception::class.java) { TemporaryProcessor(closedStore) {}.reception.receive(
                TemporaryInput(next.clientOrderId, "ABC00000001", "BROADCAST", closedStore.sample())) }
        }
    }

    @Test fun finalStartAuditFailureRollsBackOpening() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val next = acknowledged(f); val store = development(f)
            f.db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER fail_start BEFORE INSERT ON temporary_event
                WHEN NEW.type='TEMP_CONTINUATION_WORK_STARTED' BEGIN SELECT RAISE(ABORT,'injected start audit'); END""")
            assertThrows(Exception::class.java) { TemporaryContinuationWork(store).start(next.clientOrderId) }
            assertEquals(next, f.db.temporaryDrafts().get(next.clientOrderId))
            assertEquals(1, f.db.temporaryScans().events(next.clientOrderId).size)
        }
    }

    @Test fun switchedIdentityCannotReceiveInStartedWork() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val next = acknowledged(f); val store = development(f)
            TemporaryContinuationWork(store).start(next.clientOrderId)
            f.work.fixture.auth!!.put("userId", java.util.UUID.randomUUID().toString())
            assertThrows(Exception::class.java) { TemporaryProcessor(store) {}.reception.receive(
                TemporaryInput(next.clientOrderId, "ABC00000001", "BROADCAST", store.sample())) }
            assertTrue(f.db.temporaryScans().captures(next.clientOrderId).isEmpty())
        }
    }

    @Test fun modifiedHighWatermarkCannotSkipOriginalSequence() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val next = acknowledged(f); val store = development(f)
            TemporaryContinuationWork(store).start(next.clientOrderId)
            f.db.openHelper.writableDatabase.execSQL("UPDATE temporary_draft SET nextScanSeq=99 WHERE clientOrderId=?", arrayOf(next.clientOrderId))
            assertThrows(Exception::class.java) { TemporaryProcessor(store) {}.reception.receive(
                TemporaryInput(next.clientOrderId, "ABC00000001", "BROADCAST", store.sample())) }
            assertTrue(f.db.temporaryScans().captures(next.clientOrderId).isEmpty())
        }
    }

    private fun development(f: TemporaryContinuationFixture) = TemporaryWorkStore(f.db, f.work.fixture, true, f.work.store.sample)
    private fun acknowledged(f: TemporaryContinuationFixture): TemporaryDraft {
        val next = f.activate(); val ticket = f.receipt.getJSONObject("businessDecision").getString("ticketId")
        f.work.fixture.ack = f.downloadAck(checkNotNull(f.db.temporary().request(ticket)))
        TemporaryContinuationDownload(f.work.store).acknowledge(ticket)
        return next
    }
}
