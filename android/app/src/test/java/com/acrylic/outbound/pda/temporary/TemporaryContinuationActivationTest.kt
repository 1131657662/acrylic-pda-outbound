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

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryContinuationActivationTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun originalSourceSurvivesDurableActivationAndExactReplay() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val draft = f.draft; val request = f.work.catalog.request; val preparation = f.work.catalog.preparation
            val scans = f.db.temporaryScans().allScans(f.work.id); val stop = f.db.localE2aStops().get(f.work.id)
            val barcode = f.work.barcodes[0]
            val saved = f.activate(); f.work.reopen()
            assertEquals(saved, f.activate()); assertEquals(draft, f.draft)
            assertEquals(f.frozen, f.db.temporarySubmissions().forOrder(f.work.id))
            val successor = checkNotNull(f.db.temporary().request(f.receipt.getJSONObject("businessDecision").getString("ticketId")))
            assertEquals(request.key, successor.previousKey)
            assertNull(f.db.temporary().current(request.userId, request.deviceId, request.warehouseId))
            assertEquals(preparation, f.db.temporary().byAuthorization(draft.authorizationId))
            assertEquals(scans, f.db.temporaryScans().allScans(f.work.id)); assertEquals(stop, f.db.localE2aStops().get(f.work.id))
            assertEquals("CONTINUATION_BLOCKED", saved.state); assertFalse(saved.canScan())
            assertEquals(1, f.db.temporaryScans().events(saved.clientOrderId).count { it.type == "TEMP_CONTINUATION_ACTIVATED" })
            f.work.store.load(saved.clientOrderId)
            assertThrows(Exception::class.java) { f.work.processor.reception.receive(TemporaryInput(saved.clientOrderId,
                barcode, "BROADCAST", f.work.store.sample())) }
            f.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }

    @Test fun successorDownloadAcknowledgementIsResumableWithoutOpeningScanning() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val saved = f.activate()
            val ticket = f.receipt.getJSONObject("businessDecision").getString("ticketId")
            val request = checkNotNull(f.db.temporary().request(ticket))
            f.work.fixture.ack = f.downloadAck(request)
            val beforeAcks = f.work.fixture.calls.count { it.path.endsWith("download-ack") }
            val row = TemporaryContinuationDownload(f.work.store).acknowledge(ticket)
            assertNotNull(row.ackJson)
            assertEquals("CONTINUATION_BLOCKED", saved.state)
            assertFalse(f.db.temporaryDrafts().get(saved.clientOrderId)!!.canScan())
            assertNull(f.work.fixture.repository.local())
            assertThrows(Exception::class.java) { TemporaryDraftRepository(f.work.store).create(java.util.UUID.randomUUID().toString()) }
            val replay = TemporaryContinuationDownload(f.work.store).acknowledge(ticket)
            assertEquals(row, replay)
            assertEquals(beforeAcks + 1, f.work.fixture.calls.count { it.path.endsWith("download-ack") })
        }
    }

    @Test fun failedSuccessorDownloadAcknowledgementLeavesProofPending() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val saved = f.activate()
            val ticket = f.receipt.getJSONObject("businessDecision").getString("ticketId")
            val request = checkNotNull(f.db.temporary().request(ticket))
            f.work.fixture.ack = f.downloadAck(request)
            f.work.fixture.beforeCall = { if (it.path.endsWith("download-ack")) error("injected continuation ack failure") }
            assertThrows(Exception::class.java) { TemporaryContinuationDownload(f.work.store).acknowledge(ticket) }
            assertNull(f.db.temporary().prepared(request.key)!!.ackJson)
            assertEquals("CONTINUATION_BLOCKED", f.db.temporaryDrafts().get(saved.clientOrderId)!!.state)
            f.work.fixture.beforeCall = {}
            assertNotNull(TemporaryContinuationDownload(f.work.store).acknowledge(ticket).ackJson)
        }
    }

    @Test fun changedSuccessorPredecessorCannotAcknowledgeDownload() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val saved = f.activate()
            val ticket = f.receipt.getJSONObject("businessDecision").getString("ticketId")
            f.db.openHelper.writableDatabase.execSQL(
                "UPDATE temporary_request SET previousKey=NULL WHERE `key`=?", arrayOf(ticket))
            assertThrows(Exception::class.java) { TemporaryContinuationDownload(f.work.store).acknowledge(ticket) }
            assertNull(f.db.temporary().prepared(ticket)!!.ackJson)
            assertEquals("CONTINUATION_BLOCKED", f.db.temporaryDrafts().get(saved.clientOrderId)!!.state)
        }
    }

    @Test fun lastWriteFailureRollsBackNewAuthorizationPartsAndDraft() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val draft = f.draft; val decision = f.receipt.getJSONObject("businessDecision")
            f.db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER fail_continuation BEFORE INSERT ON temporary_continuation
                BEGIN SELECT RAISE(ABORT,'injected final consumption failure'); END""")
            val failure = assertThrows(Exception::class.java) { f.activate() }
            assertTrue(generateSequence<Throwable>(failure) { it.cause }.any { it.message.orEmpty().contains("injected final consumption failure") })
            f.work.reopen()
            assertEquals(draft, f.draft); assertNull(f.db.temporaryContinuations().forSource(f.work.id))
            assertNull(f.db.temporary().request(decision.getString("ticketId")))
            assertNull(f.db.temporary().byAuthorization(decision.getString("newAuthorizationId")))
            assertNull(f.db.temporaryDrafts().get(decision.getString("newClientOrderId")))
            assertEquals(0, f.db.temporary().count(decision.getString("newAuthorizationId"), "inventory"))
        }
    }

    @Test fun conflictingSignedReplayCannotReplaceConsumedTicket() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val saved = f.activate(); val before = f.db.temporaryContinuations().forSource(f.work.id)
            val changed = f.raw { it.put("recordedAt", "2026-10-06T00:00:00Z") }
            assertThrows(Exception::class.java) { f.activate(changed) }
            assertEquals(before, f.db.temporaryContinuations().forSource(f.work.id))
            assertEquals(saved, f.db.temporaryDrafts().get(saved.clientOrderId))
        }
    }

    @Test fun changedSessionCannotConsumeOriginalReceipt() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val raw = f.raw(); f.work.fixture.auth!!.put("userAuthVersion", "99")
            assertThrows(Exception::class.java) { f.activate(raw) }
            assertNull(f.db.temporaryContinuations().forSource(f.work.id))
        }
    }

    @Test fun expiredNewAuthorizationCannotBeConsumedForTheFirstTime() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val raw = f.raw(); f.work.fixture.time = f.work.fixture.time.plusSeconds(172801)
            assertThrows(Exception::class.java) { f.activate(raw) }
            assertNull(f.db.temporaryContinuations().forSource(f.work.id))
            assertNull(f.db.temporary().request(f.receipt.getJSONObject("businessDecision").getString("ticketId")))
        }
    }

    @Test fun alreadyConsumedReceiptReplaysAfterExpiryWithoutGrantingScanning() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val raw = f.raw(); val saved = f.activate(raw)
            f.work.fixture.time = f.work.fixture.time.plusSeconds(172801); f.work.reopen()
            assertEquals(saved, f.activate(raw)); assertFalse(saved.canScan())
            assertEquals(1, f.db.temporaryScans().events(saved.clientOrderId).size)
        }
    }
}
