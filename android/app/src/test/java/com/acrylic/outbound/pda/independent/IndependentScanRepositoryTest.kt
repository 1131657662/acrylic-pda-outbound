package com.acrylic.outbound.pda.independent

import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class IndependentScanRepositoryTest {
    @Test fun sourceAndFixedSkuRowSurviveSaveVoidRescanAndReopen() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentScanFixture().use { f ->
            f.start(); val first = f.save(f.input(" 003a0000001\r\n"))
            val original = checkNotNull(f.store.dao.scan(first.capture.id))
            assertEquals(" 003a0000001\r\n", first.capture.rawInput); assertEquals(15501L, original.weightGrams)
            f.corrections.voidSelected(f.command.id, first.capture.id, "已核实卸下")
            assertEquals(original, f.store.dao.scan(first.capture.id)); assertNull(f.store.dao.guard(original.barcode))
            assertEquals(0L, f.store.dao.total(f.command.id).count)
            val second = f.save(); f.reopen()
            assertEquals(1L, f.store.dao.total(f.command.id).count); assertEquals(15501L, f.store.dao.total(f.command.id).grams)
            assertEquals("VOID", f.store.dao.page(f.command.id, 0).first().status)
            assertEquals("ACTIVE", f.store.dao.locate(f.command.id, original.barcode)?.status)
            assertEquals(second.capture.id, f.store.dao.guard(original.barcode)?.captureId)
            assertEquals(2L, second.capture.deviceSequence); assertNull(f.task.sku.db.work().device(f.task.actor.deviceId))
        }
    }
    @Test fun manualInputRequiresExplicitConfirmAndMayDiscardBeforeIt() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentScanFixture().use { f ->
            f.start(); val row = f.reception.receive(f.input(source = "MANUAL")); f.reception.validate(row.capture.id)
            assertEquals("WAITING_CONFIRM", f.store.dao.capture(row.capture.id)?.processing?.state)
            assertThrows(IllegalStateException::class.java) { f.saving.save(row.capture.id) }
            f.corrections.discard(row.capture.id, "实物未装车，放弃本次手输")
            assertEquals(0, f.store.dao.unresolved()); assertEquals(0L, f.store.dao.total(f.command.id).count)
            val saved = f.save(f.input(source = "MANUAL"))
            assertNotNull(saved.processing.confirmedAt)
            assertThrows(IllegalStateException::class.java) { f.corrections.discard(saved.capture.id, "不能弃已确认") }
        }
    }
    @Test fun duplicateAndUnknownBarcodeRequireAcknowledgementWithoutDeletingInput() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentScanFixture().use { f ->
            f.start(); f.save()
            val bad = f.reception.receive(f.input()); f.reception.validate(bad.capture.id)
            assertEquals("INVALID", f.store.dao.capture(bad.capture.id)?.processing?.state)
            assertEquals(1, f.store.dao.unresolved())
            f.corrections.acknowledge(bad.capture.id); assertEquals(0, f.store.dao.unresolved())
            val missing = f.reception.receive(f.input("004A0000001")); f.reception.validate(missing.capture.id)
            assertTrue(f.store.dao.failure(f.command.id)!!.processing.error.contains("固定原sku"))
            assertEquals(1L, f.store.dao.total(f.command.id).count); assertEquals(3, f.store.dao.captures(f.command.id).size)
        }
    }
    @Test fun originalInputRetryCannotChangeRawSourceOrTime() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentScanFixture().use { f ->
            f.start(); val input = f.input(); val original = f.reception.receive(input)
            assertEquals(original, f.reception.receive(input))
            assertThrows(IllegalStateException::class.java) { f.reception.receive(input.copy(raw = "bad")) }
            assertThrows(IllegalStateException::class.java) { f.reception.receive(input.copy(source = "MANUAL")) }
            assertThrows(IllegalStateException::class.java) { f.reception.receive(input.copy(time = input.time.copy(elapsed = input.time.elapsed - 1))) }
            assertEquals(1, f.store.dao.captures(f.command.id).size)
        }
    }
    @Test fun originalCatalogAndCredentialCannotBeReplacedForFurtherScanning() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentScanFixture().use { f ->
            f.start(); f.task.sku.activate("004A0000001"); val row = f.save()
            assertEquals("003A0000001", f.store.dao.scan(row.capture.id)?.barcode)
            f.task.identity.provision()
            val replacement = IndependentScanStore(f.task.sku.db, f.task.grant(), f.environment)
            assertThrows(IllegalStateException::class.java) { replacement.open(f.command.id) }
            f.task.identity.advance(48 * 3600 * 1000L)
            assertThrows(IllegalStateException::class.java) { f.reception.receive(f.input()) }
            assertEquals(1L, f.store.dao.total(f.command.id).count)
        }
    }
    @Test fun saveAuditTailRollbackPreservesPendingOriginalForNewDatabaseConnection() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentScanFixture().use { f ->
            f.start(); val c = f.reception.receive(f.input()); f.reception.validate(c.capture.id)
            val db = f.task.sku.db.openHelper.writableDatabase
            db.execSQL("""CREATE TRIGGER isolated_save_tail BEFORE INSERT ON independent_task_event
                WHEN NEW.type='INDEPENDENT_SAVED' BEGIN SELECT RAISE(ABORT,'isolated scan tail'); END""")
            assertThrows(Exception::class.java) { f.saving.save(c.capture.id) }
            assertNull(f.store.dao.scan(c.capture.id)); assertNull(f.store.dao.guard(c.capture.barcode))
            assertEquals("CONFIRM_REQUESTED", f.store.dao.capture(c.capture.id)?.processing?.state)
            assertEquals(0L, f.task.repository.read(f.command.id).state.actualCount)
            f.reopen(); f.task.sku.db.openHelper.writableDatabase.execSQL("DROP TRIGGER isolated_save_tail")
            assertEquals("SAVED", f.saving.save(c.capture.id)); assertEquals("ALREADY_SAVED", f.saving.save(c.capture.id))
            assertEquals(1L, f.task.repository.read(f.command.id).state.actualCount)
        }
    }
    @Test fun voidAuditTailRollbackKeepsOriginalEffectiveAndRetryIsOneVoid() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentScanFixture().use { f ->
            f.start(); val c = f.save()
            val db = f.task.sku.db.openHelper.writableDatabase
            db.execSQL("""CREATE TRIGGER isolated_void_tail BEFORE INSERT ON independent_task_event
                WHEN NEW.type='INDEPENDENT_VOID' BEGIN SELECT RAISE(ABORT,'isolated void tail'); END""")
            assertThrows(Exception::class.java) { f.corrections.voidSelected(f.command.id, c.capture.id, "核实已卸下") }
            assertNull(f.store.dao.voided(c.capture.id)); assertNotNull(f.store.dao.guard(c.capture.barcode))
            f.reopen(); f.task.sku.db.openHelper.writableDatabase.execSQL("DROP TRIGGER isolated_void_tail")
            f.corrections.voidSelected(f.command.id, c.capture.id, "核实已卸下")
            f.corrections.voidSelected(f.command.id, c.capture.id, "核实已卸下")
            assertEquals(1, f.store.dao.voids(f.command.id).size)
            assertThrows(IllegalStateException::class.java) { f.corrections.voidSelected(f.command.id, c.capture.id, "异文") }
        }
    }
}
