package com.acrylic.outbound.pda.work

import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class WorkFlowTest {
    @get:Rule val folder = TemporaryFolder()
    private fun scenario(block: (WorkFixture) -> Unit) = runBlocking<Unit>(Dispatchers.IO) { WorkFixture(folder.root).use(block) }

    @Test fun automaticScanStoresExactWeightIdentityCycleAndOrderedAudit() = scenario { f ->
        val capture = f.scan("  ${f.barcodes[0].lowercase()}\r\n")
        val scan = f.db.scans().get(capture.id)!!
        assertEquals(300500, scan.weightGrams); assertEquals("300.5", scan.rawWeight)
        assertEquals(1, f.db.work().order(f.assignment)!!.actualCount)
        assertEquals(1, scan.expectedDispatchCycle); assertEquals(capture.id, f.db.scans().guard(scan.barcode)!!.captureId)
        assertEquals(f.input.session.getString("userId"), capture.context.operatorId)
        assertEquals("AUTO_AFTER_VALIDATION", capture.context.confirmationPolicy)
        assertEquals(listOf("WORK_OPENED", "RECEIVED", "CONFIRM_REQUESTED", "SAVED"), f.db.work().events(f.assignment).map { it.type })
        assertEquals(listOf("SAVED"), f.sounds); f.store.verifyTotals(f.assignment)
    }

    @Test fun manualCandidateRequiresItsOwnConfirmationAndBlocksLaterAutomaticInput() = scenario { f ->
        val manual = f.receive(source = "MANUAL"); f.processor.drain(f.assignment)
        val auto = f.receive(f.barcodes[1]); f.processor.drain(f.assignment)
        assertEquals("WAITING_CONFIRM", f.db.captures().get(manual.id)!!.state)
        assertEquals(0, f.db.scans().total(f.assignment).count)
        f.processor.saving.confirm(manual.id); f.processor.drain(f.assignment)
        f.processor.saving.confirm(manual.id); f.processor.drain(f.assignment)
        assertEquals(listOf(manual.id, auto.id), f.db.scans().page(f.assignment, 0, 10).map { it.captureId })
        assertEquals(601500, f.db.scans().total(f.assignment).grams)
    }

    @Test fun invalidInputPausesQueueAndAcknowledgementCannotConfirmNextManualCandidate() = scenario { f ->
        val invalid = f.scan("bad"); val manual = f.receive(f.barcodes[0], "MANUAL"); val auto = f.receive(f.barcodes[1])
        f.processor.drain(f.assignment)
        assertEquals(0, f.db.scans().total(f.assignment).count)
        assertEquals(2, f.db.captures().pendingCount(f.assignment))
        f.processor.corrections.acknowledge(invalid.id); f.processor.drain(f.assignment)
        f.processor.corrections.acknowledge(invalid.id); f.processor.drain(f.assignment)
        assertEquals("WAITING_CONFIRM", f.db.captures().get(manual.id)!!.state)
        assertNull(f.db.scans().get(auto.id)); assertEquals(listOf("ERROR"), f.sounds)
        assertEquals(listOf(1L, 2L, 3L), f.db.captures().page(f.assignment, 0, 10).map { it.context.orderScanSeq })
    }

    @Test fun duplicateScanRemainsVisibleAndDoesNotIncreaseWeight() = scenario { f ->
        f.scan(); val duplicate = f.scan()
        assertEquals("INVALID", f.db.captures().get(duplicate.id)!!.state)
        assertTrue(f.db.captures().get(duplicate.id)!!.error.contains("本机"))
        assertEquals(1, f.db.scans().total(f.assignment).count); assertEquals(300500, f.db.scans().total(f.assignment).grams)
        assertEquals(listOf("SAVED", "ERROR"), f.sounds)
    }

    @Test fun voidKeepsOriginalEvidenceThenRescanUsesNewSequenceAndExactTotals() = scenario { f ->
        val first = f.scan()
        f.processor.corrections.voidLast(f.assignment, first.id, "实物核对未装车，撤销误扫")
        assertEquals("COMMITTED", f.db.captures().get(first.id)!!.state)
        assertEquals("VOID", f.db.scans().get(first.id)!!.status); assertNull(f.db.scans().guard(f.barcodes[0]))
        val second = f.scan(); assertEquals(2, second.context.orderScanSeq)
        assertEquals(1, f.db.scans().total(f.assignment).count); f.store.verifyTotals(f.assignment)
        assertTrue(f.db.work().events(f.assignment).any { it.type == "VOID" && it.detail.contains("实物") })
    }

    @Test fun onlyUnconfirmedManualInputCanBeDiscarded() = scenario { f ->
        val manual = f.receive(source = "MANUAL"); f.processor.drain(f.assignment)
        f.processor.corrections.discard(manual.id, "重新核对手输字符")
        val automatic = f.receive(f.barcodes[1]); f.processor.reception.validate(automatic.id)
        assertThrows(IllegalStateException::class.java) { f.processor.corrections.discard(automatic.id, "不得清除") }
        assertEquals("DISCARDED", f.db.captures().get(manual.id)!!.state)
        f.processor.drain(f.assignment); assertEquals(1, f.db.scans().total(f.assignment).count)
    }
}
