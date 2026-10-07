package com.acrylic.outbound.pda.temporary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.IOException

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryContinuationQueueTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun lostResponseReplaysOriginalAckKeyAfterRoomRestart() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val next = f.activate(); val ticket = ticket(f)
            val request = checkNotNull(f.db.temporary().request(ticket)); f.work.fixture.ack = f.downloadAck(request)
            val before = checkNotNull(f.db.temporary().prepared(ticket))
            val coordinator = coordinator(f); coordinator.queue(ticket)
            f.work.fixture.beforeCall = { if (it.path.endsWith("download-ack")) throw IOException("lost response") }
            assertThrows(IOException::class.java) { coordinator.resume(ticket) }
            assertEquals("PENDING", f.db.temporaryContinuationQueue().get(ticket)!!.state)
            assertEquals("CONTINUATION_BLOCKED", f.db.temporaryDrafts().get(next.clientOrderId)!!.state)
            f.work.fixture.beforeCall = {}; f.work.reopen()
            assertEquals("READY", coordinator(f).resume(ticket).state)
            val sent = f.work.fixture.calls.filter { it.path.endsWith("download-ack") && it.key == before.ackKey }
            assertEquals(2, sent.size); assertEquals(sent[0].body.toString(), sent[1].body.toString())
            assertEquals(before.ackBodyJson, f.db.temporary().prepared(ticket)!!.ackBodyJson)
            assertEquals("SCANNING", f.db.temporaryDrafts().get(next.clientOrderId)!!.state)
            val count = f.work.fixture.calls.size
            assertEquals("READY", coordinator(f).resume(ticket).state); assertEquals(count, f.work.fixture.calls.size)
        }
    }

    @Test fun finalQueueWriteFailureKeepsAckButRollsBackWorkStart() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val next = f.activate(); val ticket = ticket(f)
            f.work.fixture.ack = f.downloadAck(checkNotNull(f.db.temporary().request(ticket)))
            val coordinator = coordinator(f); coordinator.queue(ticket)
            f.db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER fail_queue BEFORE UPDATE ON temporary_continuation_queue
                WHEN NEW.state='READY' BEGIN SELECT RAISE(ABORT,'injected queue write'); END""")
            assertThrows(Exception::class.java) { coordinator.resume(ticket) }
            assertNotNull(f.db.temporary().prepared(ticket)!!.ackJson)
            assertEquals("CONTINUATION_BLOCKED", f.db.temporaryDrafts().get(next.clientOrderId)!!.state)
            assertEquals(0, f.db.temporaryScans().events(next.clientOrderId).count { it.type == "TEMP_CONTINUATION_WORK_STARTED" })
            assertEquals("ACTION_REQUIRED", f.db.temporaryContinuationQueue().get(ticket)!!.state)
            f.db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_queue")
            val calls = f.work.fixture.calls.size; coordinator.queue(ticket); coordinator.resume(ticket)
            assertEquals(calls, f.work.fixture.calls.size)
            assertEquals("SCANNING", f.db.temporaryDrafts().get(next.clientOrderId)!!.state)
        }
    }

    @Test fun switchedSessionCannotSendOrCompleteQueuedIntent() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val next = f.activate(); val ticket = ticket(f); val coordinator = coordinator(f)
            coordinator.queue(ticket); val calls = f.work.fixture.calls.size
            f.work.fixture.auth!!.put("deviceId", java.util.UUID.randomUUID().toString())
            assertThrows(Exception::class.java) { coordinator.resume(ticket) }
            assertEquals(calls, f.work.fixture.calls.size)
            assertEquals("CONTINUATION_BLOCKED", f.db.temporaryDrafts().get(next.clientOrderId)!!.state)
            assertEquals("PENDING", f.db.temporaryContinuationQueue().get(ticket)!!.state)
        }
    }

    private fun ticket(f: TemporaryContinuationFixture) = f.receipt.getJSONObject("businessDecision").getString("ticketId")
    private fun coordinator(f: TemporaryContinuationFixture) = TemporaryContinuationCoordinator(
        TemporaryWorkStore(f.db, f.work.fixture, true, f.work.store.sample))
}
