package com.acrylic.outbound.pda.work

import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class WorkAdjustmentTest {
    @get:Rule val folder = TemporaryFolder()
    private fun scenario(block: (WorkFixture) -> Unit) = runBlocking<Unit>(Dispatchers.IO) { WorkFixture(folder.root).use(block) }

    @Test fun olderPackageCanBeVoidedWithoutLosingOriginalSequenceOrLaterPackage() = scenario { f ->
        val first = f.scan(); val last = f.scan(f.barcodes[1])
        f.processor.corrections.voidSelected(f.assignment, first.id, "实物已卸下，调整较早的包")
        f.reload()
        assertEquals("COMMITTED", f.db.captures().get(first.id)?.state)
        assertEquals("VOID", f.db.scans().get(first.id)?.status)
        assertEquals("ACTIVE", f.db.scans().get(last.id)?.status)
        assertEquals(listOf(1L, 2L), f.db.scans().page(f.assignment, 0, 20).map { it.orderScanSeq })
        assertNull(f.db.scans().guard(f.barcodes[0])); f.store.verifyTotals(f.assignment)
        val again = f.scan()
        assertEquals(3L, again.context.orderScanSeq); assertEquals(2L, f.db.scans().total(f.assignment).count)
        assertEquals(again.id, f.db.scans().locate(f.assignment, f.barcodes[0])?.captureId)
        assertEquals(1, f.db.work().events(f.assignment).count { it.type == "VOID" })
    }

    @Test fun lastAuditFailureRollsBackVoidGuardAndTotals() = scenario { f ->
        val first = f.scan(); f.scan(f.barcodes[1]); val before = f.db.work().order(f.assignment)
        f.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_adjustment BEFORE INSERT ON work_event WHEN NEW.type='VOID' BEGIN SELECT RAISE(ABORT,'adjustment audit failure'); END")
        assertThrows(Exception::class.java) { f.processor.corrections.voidSelected(f.assignment, first.id, "实物核对") }
        assertEquals(before, f.db.work().order(f.assignment)); assertEquals("ACTIVE", f.db.scans().get(first.id)?.status)
        assertNotNull(f.db.scans().guard(f.barcodes[0])); f.store.verifyTotals(f.assignment)
        assertFalse(f.db.work().events(f.assignment).any { it.type == "VOID" })
    }

    @Test fun packagesOutsideRecentWindowCanBeLocatedAndAdjustedInSequence() = scenario { f ->
        val originals = (1..8).map { index ->
            val barcode = "003Z" + index.toString().padStart(7, '0')
            f.db.preparations().insertBales(listOf(com.acrylic.outbound.pda.data.PreparedBale(f.assignment, barcode,
                java.util.UUID.randomUUID().toString(), "003", "合成型号", "合成产品", "0.001", 1, 1, true)))
            f.scan(barcode)
        }
        assertFalse(f.db.scans().recent(f.assignment).any { it.captureId == originals.first().id })
        val target = f.db.scans().locate(f.assignment, originals.first().context.barcode)!!
        f.processor.corrections.voidSelected(f.assignment, target.captureId, "较早原包实物已卸下")
        assertEquals(7L, f.db.scans().total(f.assignment).count); assertEquals(7L, f.db.scans().total(f.assignment).grams)
        assertEquals((1L..8L).toList(), f.db.scans().page(f.assignment, 0, 20).map { it.orderScanSeq }); f.store.verifyTotals(f.assignment)
        originals.drop(1).take(5).forEach { f.processor.corrections.voidSelected(f.assignment, it.id, "逐包实物已卸下，保留原记录") }
        assertEquals(2L, f.db.scans().total(f.assignment).count); assertEquals(2L, f.db.scans().total(f.assignment).grams)
        assertEquals(6, f.db.work().events(f.assignment).count { it.type == "VOID" })
        assertEquals((1L..8L).toList(), f.db.scans().page(f.assignment, 0, 20).map { it.orderScanSeq }); f.store.verifyTotals(f.assignment)
    }

    @Test fun pendingInputAndStoppedOrderCannotAdjustOldFacts() = scenario { f ->
        val first = f.scan(); f.receive(f.barcodes[1], "MANUAL")
        assertThrows(Exception::class.java) { f.processor.corrections.voidSelected(f.assignment, first.id, "实物核对") }
        f.db.work().recall(f.assignment)
        assertThrows(Exception::class.java) { f.processor.corrections.voidSelected(f.assignment, first.id, "实物核对") }
        assertEquals("ACTIVE", f.db.scans().get(first.id)?.status); assertNotNull(f.db.scans().guard(f.barcodes[0]))
    }

    @Test fun wrongOrderIdentityAndRepeatedConfirmationCannotVoid() = scenario { f ->
        val first = f.scan()
        assertThrows(Exception::class.java) { f.processor.corrections.voidSelected("another-order", first.id, "实物核对") }
        val user = f.session.getString("userId"); f.session.put("userId", java.util.UUID.randomUUID().toString())
        assertThrows(Exception::class.java) { f.processor.corrections.voidSelected(f.assignment, first.id, "实物核对") }
        f.session.put("userId", user); f.processor.corrections.voidSelected(f.assignment, first.id, "实物核对")
        assertThrows(Exception::class.java) { f.processor.corrections.voidSelected(f.assignment, first.id, "重复确认") }
        assertEquals(1, f.db.work().events(f.assignment).count { it.type == "VOID" })
    }

    @Test fun supervisorRetainedPrefixRejectsAdjustmentWithoutChangingOriginalFacts() = scenario { f ->
        val first = f.scan(); f.scan(f.barcodes[1]); val before = f.db.work().order(f.assignment)
        f.db.continuations().insert(com.acrylic.outbound.pda.continuation.LocalContinuation("retained-prefix", f.assignment,
            f.input.assignment.toString(), "{}", 2, 2, "synthetic-supervisor-proof"))
        val error = assertThrows(Exception::class.java) { f.processor.corrections.voidSelected(f.assignment, first.id, "实物核对") }
        assertTrue(error.message.orEmpty().contains("主管核验保留"))
        assertEquals(before, f.db.work().order(f.assignment)); assertEquals("ACTIVE", f.db.scans().get(first.id)?.status)
        assertNotNull(f.db.scans().guard(first.context.barcode)); assertFalse(f.db.work().events(f.assignment).any { it.type == "VOID" })
    }

    @Test fun supervisorDispositionCannotBeReplacedWithLocalVoid() = scenario { f ->
        val first = f.scan(); val last = f.scan(f.barcodes[1]); val scan = f.db.scans().get(first.id)!!
        f.db.dispositions().insert(com.acrylic.outbound.pda.continuation.LocalDisposition(first.id, f.assignment,
            "synthetic-disposition", "QUARANTINE", "{}", 1))
        val remaining = f.db.scans().total(f.assignment)
        f.db.work().totals(f.assignment, remaining.count, remaining.grams, null)
        f.db.work().lineTotals(f.assignment, scan.deliveryCode, 0, 0)
        f.store.verifyTotals(f.assignment)
        val error = assertThrows(Exception::class.java) { f.processor.corrections.voidSelected(f.assignment, first.id, "实物核对") }
        assertTrue(error.message.orEmpty().contains("主管处置"))
        assertEquals("ACTIVE", f.db.scans().get(first.id)?.status); assertEquals("ACTIVE", f.db.scans().get(last.id)?.status)
        assertEquals(1, f.db.dispositions().all(f.assignment).size)
        assertFalse(f.db.work().events(f.assignment).any { it.type == "VOID" })
    }

    @Test fun expiredQualificationAndForegroundWriterRejectAdjustment() = scenario { f ->
        val first = f.scan(); val now = f.now; f.now = now.plusSeconds(172801)
        assertThrows(Exception::class.java) { f.processor.corrections.voidSelected(f.assignment, first.id, "实物核对") }
        f.now = now
        runBlocking {
            val engine = WorkEngine(f.store) {}
            try {
                engine.resume(f.assignment); withTimeout(10000) { while (!engine.state.value.ready) delay(10) }
                assertTrue(runCatching { engine.adjust(f.assignment, first.id, "实物核对").await() }.isFailure)
                engine.pause(); engine.adjust(f.assignment, first.id, "实物核对").await()
                assertEquals("VOID", f.db.scans().get(first.id)?.status)
            } finally { engine.close() }
        }
    }
}
