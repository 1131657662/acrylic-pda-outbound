package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.submission.*
import com.acrylic.outbound.pda.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class HandoverContinuationTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun verifiedReceiptEnablesNewDeviceInputWithoutChangingImportedPrefix() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverContinuationFixture(folder.root).use { f ->
            val source = f.local.db.handoverStages().get(f.local.caseId)!!
            val prepared = f.local.db.preparations().order(f.local.target)
            assertEquals(f.local.target, f.activate())
            assertEquals("SCANNING", f.local.db.work().state(f.local.target))
            val processor = WorkProcessor(f.store) { _, _ -> }
            val inventory = org.json.JSONArray(String(f.local.input.payloads.getValue("inventory")))
            val next = inventory.getJSONObject(1).getString("barcode")
            val captured = processor.reception.receive(WorkInput(f.local.target, next, "BROADCAST", f.access.sample()))
            processor.drain(f.local.target)
            assertEquals(1L, captured.context.deviceCaptureSeq)
            assertEquals(f.local.body.getLong("captureHighWatermark") + 1, captured.context.orderScanSeq)
            val frozen = SubmissionFreeze(f.store) { f.download.clock }.freeze(f.local.target, "核对短装")
            val body = JSONObject(frozen.payload)
            assertEquals(f.receipt.getString("continuationId"), body.getString("continuationId"))
            for (field in listOf("captures", "scans", "events")) {
                val old = f.local.body.getJSONArray(field)
                for (i in 0 until old.length()) assertEquals(SubmissionJson.canonical(old.getJSONObject(i)),
                    SubmissionJson.canonical(body.getJSONArray(field).getJSONObject(i)))
            }
            assertEquals(prepared, f.local.db.preparations().order(f.local.target))
            assertArrayEquals(source.recoveryBytes, f.local.db.handoverStages().get(f.local.caseId)!!.recoveryBytes)
            assertEquals(f.local.target, f.activate())
            assertEquals(1, f.bodies.size)
        }
    }

    @Test fun lostResponseReplaysDurableOriginalAfterDatabaseReopen() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverContinuationFixture(folder.root).use { f ->
            f.download.afterResponse = { if (it.endsWith("/continuation")) throw IOException("Injected lost receipt") }
            assertThrows(IOException::class.java) { f.activate() }
            assertFrozen(f)
            val row = f.local.db.handoverContinuations().get(f.local.caseId)
            f.local.reopen()
            f.download.afterResponse = {}
            f.activate()
            assertEquals(f.bodies.first(), f.bodies.last())
            assertEquals(row!!.commandId, f.local.db.handoverContinuations().get(f.local.caseId)!!.commandId)
            assertEquals("SCANNING", f.local.db.work().state(f.local.target))
        }
    }

    @Test fun socketDisconnectKeepsFrozenCommandForRetry() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverContinuationFixture(folder.root).use { f ->
            f.respond = { MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) }
            assertThrows(IOException::class.java) { f.activate() }
            assertFrozen(f)
            f.respond = { f.json(f.receipt) }
            f.local.reopen()
            f.activate()
            assertEquals(f.bodies.first(), f.bodies.last())
        }
    }

    @Test fun serverRejectionPreservesCommandAndDoesNotThaw() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverContinuationFixture(folder.root).use { f ->
            f.respond = { MockResponse().setResponseCode(409).setBody("{\"message\":\"已拒绝\"}") }
            assertThrows(Exception::class.java) { f.activate() }
            assertFrozen(f)
            assertNotNull(f.local.db.handoverContinuations().get(f.local.caseId))
        }
    }

    @Test fun concurrentCallersUseOneOriginalCommandAndOneLocalContinuation() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverContinuationFixture(folder.root).use { f ->
            val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
            val gate = java.util.concurrent.CountDownLatch(1)
            try {
                val calls = (1..2).map { pool.submit<String> { gate.await(); f.activate() } }
                gate.countDown()
                calls.forEach { assertEquals(f.local.target, it.get(30, java.util.concurrent.TimeUnit.SECONDS)) }
                assertEquals(1, f.bodies.toSet().size)
                assertEquals(f.receipt.getString("continuationId"), f.local.db.continuations().current(f.local.target)!!.id)
            } finally { pool.shutdownNow() }
        }
    }

    @Test fun localCommitFailureRollsBackCredentialAndThawTogether() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverContinuationFixture(folder.root).use { f ->
            f.local.db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER fail_continuation BEFORE UPDATE ON local_handover_continuation
                BEGIN SELECT RAISE(ABORT, 'injected commit failure'); END""")
            assertThrows(Exception::class.java) { f.activate() }
            assertFrozen(f)
            f.local.db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_continuation")
            f.local.reopen()
            f.activate()
            assertEquals(f.bodies.first(), f.bodies.last())
        }
    }

    private fun assertFrozen(f: HandoverContinuationFixture) {
        assertEquals("HANDOVER_FROZEN", f.local.db.work().state(f.local.target))
        assertNull(f.local.db.continuations().current(f.local.target))
        assertNull(f.local.db.handoverContinuations().get(f.local.caseId)?.continuationId)
        assertThrows(Exception::class.java) { WorkReception(f.store).receive(WorkInput(f.local.target, "TEST", "BROADCAST", f.access.sample())) }
    }
}
