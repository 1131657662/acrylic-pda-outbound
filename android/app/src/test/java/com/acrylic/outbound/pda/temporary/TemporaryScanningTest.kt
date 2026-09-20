package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.LocalRecoveryControl
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryScanningTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun automaticMixedProductsCommitExactGramsAndOriginalContext() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start("1"); val codes = f.barcodes
            val first = f.scan(" \t${codes[0].lowercase()}\r\n"); val second = f.scan(codes[1])
            assertEquals("COMMITTED", f.db.temporaryScans().capture(first.id)?.state)
            assertEquals(2L, f.db.temporaryScans().total(f.id).count)
            assertEquals(f.catalog.bales.values.sumOf { it.grams }, f.db.temporaryScans().total(f.id).grams)
            assertEquals(2, f.db.temporaryScans().products(f.id).size)
            assertEquals(first.orderScanSeq + 1, second.orderScanSeq)
            assertEquals(f.catalog.preparation.authorizationId, first.authorizationId)
            assertEquals(listOf("SAVED", "SAVED"), f.feedback)
        }
    }
    @Test fun manualValidationDoesNotSaveUntilExplicitConfirmationAfterReopen() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val row = f.receive(source = "MANUAL"); f.processor.drain(f.id)
            assertEquals("WAITING_CONFIRM", f.db.temporaryScans().capture(row.id)?.state)
            assertEquals(0L, f.db.temporaryScans().total(f.id).count)
            f.reopen(); f.processor.drain(f.id); assertNull(f.db.temporaryScans().scan(row.id))
            f.processor.saving.confirm(row.id); f.reopen(); f.processor.drain(f.id)
            assertNotNull(f.db.temporaryScans().scan(row.id)); assertEquals("ALREADY_SAVED", f.processor.saving.save(row.id))
            assertEquals(1L, f.db.temporaryScans().total(f.id).count)
        }
    }
    @Test fun failedSaveRollsBackScanGuardAndEventButKeepsConfirmationForRetry() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val row = f.receive(); f.processor.reception.validate(row.id)
            val events = f.db.temporaryScans().events(f.id); val device = f.db.work().device(row.originDeviceId)
            f.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_temp_guard BEFORE INSERT ON temporary_guard BEGIN SELECT RAISE(ABORT,'injected disk failure'); END")
            assertThrows(Exception::class.java) { f.processor.saving.save(row.id) }
            assertNull(f.db.temporaryScans().scan(row.id)); assertNull(f.db.temporaryScans().guard(row.barcode))
            assertEquals("CONFIRM_REQUESTED", f.db.temporaryScans().capture(row.id)?.state)
            assertEquals(events, f.db.temporaryScans().events(f.id)); assertEquals(device, f.db.work().device(row.originDeviceId))
            assertTrue(f.feedback.isEmpty())
            f.db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_temp_guard")
            f.reopen(); f.processor.drain(f.id); assertEquals(1L, f.db.temporaryScans().total(f.id).count)
        }
    }
    @Test fun invalidInputStopsQueueUntilAcknowledgedAndKeepsSequenceGap() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val bad = f.receive("bad"); val good = f.receive()
            f.processor.drain(f.id); assertEquals("INVALID", f.db.temporaryScans().capture(bad.id)?.state)
            assertEquals("RECEIVED", f.db.temporaryScans().capture(good.id)?.state)
            f.processor.corrections.acknowledge(bad.id); f.processor.drain(f.id)
            assertEquals(2L, f.db.temporaryScans().scan(good.id)?.orderScanSeq)
            assertNotNull(f.db.temporaryScans().capture(bad.id)?.failureAckAt)
        }
    }
    @Test fun pendingInputPreventsCreatingAnotherDraftAndUpdatingInstaller() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val row = f.receive(source = "MANUAL"); f.processor.drain(f.id)
            assertThrows(Exception::class.java) { f.drafts.create(UUID.randomUUID().toString()) }
            assertNotNull(com.acrylic.outbound.pda.update.UpdateSafety(f.db) { com.acrylic.outbound.pda.work.WorkUi() }.installBlockReason())
            f.processor.corrections.discard(row.id, "实物未装车，手输放弃")
            assertEquals("DISCARDED", f.db.temporaryScans().capture(row.id)?.state)
            assertNotNull(f.drafts.create(UUID.randomUUID().toString()))
        }
    }
    @Test fun voidLastRequiresOriginalLastIdAndRetainsFactsBeforeRescan() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val first = f.scan(); val second = f.scan(f.barcodes[1])
            assertThrows(Exception::class.java) { f.processor.corrections.voidLast(f.id, first.id, "核实卸货") }
            f.processor.corrections.voidLast(f.id, second.id, "核实实物已卸下")
            assertEquals("VOID", f.db.temporaryScans().scan(second.id)?.status); assertNull(f.db.temporaryScans().guard(second.barcode))
            val again = f.scan(second.barcode)
            assertEquals(3L, f.db.temporaryScans().scan(again.id)?.orderScanSeq)
            assertEquals(2L, f.db.temporaryScans().total(f.id).count)
        }
    }
    @Test fun expiryBeforeCommitKeepsPendingIntentAndNoNewFact() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val row = f.receive(); f.processor.reception.validate(row.id)
            f.fixture.time = java.time.Instant.parse(f.catalog.manifest.getString("expiresAt"))
            assertThrows(Exception::class.java) { f.processor.saving.save(row.id) }
            assertNull(f.db.temporaryScans().scan(row.id)); assertEquals("CONFIRM_REQUESTED", f.db.temporaryScans().capture(row.id)?.state)
            assertThrows(Exception::class.java) { f.receive(f.barcodes[1]) }
        }
    }
    @Test fun recoveryOrIdentityChangeBeforeCommitCannotConsumePendingIntent() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val row = f.receive(); f.processor.reception.validate(row.id)
            f.fixture.auth!!.put("userAuthVersion", "999")
            assertThrows(Exception::class.java) { f.processor.saving.save(row.id) }
            f.fixture.auth!!.put("userAuthVersion", f.catalog.request.userAuthVersion)
            f.db.sync().saveRecovery(LocalRecoveryControl(observedServerEpoch = f.catalog.request.recoveryEpoch, mode = "RECONCILING"))
            assertThrows(Exception::class.java) { f.processor.saving.save(row.id) }
            assertNull(f.db.temporaryScans().scan(row.id)); assertEquals("CONFIRM_REQUESTED", f.db.temporaryScans().capture(row.id)?.state)
        }
    }
    @Test fun concurrentSameBarcodeWritersProduceOneFactAndOnePersistentRejection() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val inputs = (1..2).map { async(Dispatchers.IO) { f.receive() } }.awaitAll()
            f.processor.drain(f.id)
            assertEquals(1L, f.db.temporaryScans().total(f.id).count)
            assertEquals(setOf("COMMITTED", "INVALID"), inputs.map { f.db.temporaryScans().capture(it.id)!!.state }.toSet())
            assertEquals(listOf(1L, 2L), inputs.map { it.orderScanSeq }.sorted())
            assertNotNull(f.db.temporaryScans().guard(inputs.first().barcode))
        }
    }
}
