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
class WorkRecoveryTest {
    @get:Rule val folder = TemporaryFolder()
    private fun scenario(block: (WorkFixture) -> Unit) = runBlocking<Unit>(Dispatchers.IO) { WorkFixture(folder.root).use(block) }

    @Test fun transactionFailureLeavesIntentAndNoPartialScanGuardOrTotals() = scenario { f ->
        val c = f.receive(); f.processor.reception.validate(c.id)
        f.db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER fail_save BEFORE UPDATE OF actualCount ON work_line
            BEGIN SELECT RAISE(ABORT,'simulated storage failure'); END""")
        assertThrows(Exception::class.java) { f.processor.saving.save(c.id) }
        assertNull(f.db.scans().get(c.id)); assertNull(f.db.scans().guard(f.barcodes[0]))
        assertEquals(0, f.db.work().order(f.assignment)!!.actualCount)
        assertEquals("CONFIRM_REQUESTED", f.db.captures().get(c.id)!!.state)
        assertFalse(f.db.work().events(f.assignment).any { it.type == "SAVED" })
        f.db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_save"); f.reload(); f.processor.drain(f.assignment)
        assertEquals("COMMITTED", f.db.captures().get(c.id)!!.state); f.store.verifyTotals(f.assignment)
        f.sounds.clear(); f.reload(); f.processor.drain(f.assignment)
        assertEquals(1, f.db.scans().total(f.assignment).count); assertTrue(f.sounds.isEmpty())
    }

    @Test fun restartPreservesPolicyAndOriginalDeviceAndOrderSequences() = scenario { f ->
        val c = f.receive(source = "MANUAL"); f.reload(); f.processor.drain(f.assignment)
        assertEquals("WAITING_CONFIRM", f.db.captures().get(c.id)!!.state)
        assertNull(f.db.scans().get(c.id))
        f.processor.saving.confirm(c.id); f.reload(); f.processor.drain(f.assignment)
        val next = f.scan(f.barcodes[1])
        assertEquals(2, next.context.orderScanSeq); assertEquals(2, next.context.deviceCaptureSeq)
        assertEquals(2, f.db.scans().total(f.assignment).count)
    }

    @Test fun deadlineReachedBetweenIntentAndCommitStopsSavingWithoutLosingIntent() = scenario { f ->
        val c = f.receive(); f.processor.reception.validate(c.id)
        f.now = java.time.Instant.parse(f.input.assignment.getString("expiresAt"))
        assertThrows(IllegalStateException::class.java) { f.processor.saving.save(c.id) }
        assertThrows(IllegalStateException::class.java) { f.receive(f.barcodes[1]) }
        assertEquals("CONFIRM_REQUESTED", f.db.captures().get(c.id)!!.state)
        assertEquals(0, f.db.scans().total(f.assignment).count)
        assertEquals(1, f.db.captures().pendingCount(f.assignment))
    }

    @Test fun changedOperatorOrPermissionVersionCannotSaveOriginalCapture() = scenario { f ->
        val c = f.receive(); f.processor.reception.validate(c.id)
        val original = f.session.getString("userId")
        f.session.put("userId", java.util.UUID.randomUUID().toString())
        assertThrows(IllegalStateException::class.java) { f.processor.saving.save(c.id) }
        f.session.put("userId", original).put("userAuthVersion", "99")
        assertThrows(IllegalStateException::class.java) { f.processor.saving.save(c.id) }
        assertEquals("CONFIRM_REQUESTED", f.db.captures().get(c.id)!!.state); assertNull(f.db.scans().get(c.id))
    }

    @Test fun cycleCapturedAtReceptionCannotBeReplacedByNewerState() = scenario { f ->
        val c = f.receive()
        f.db.openHelper.writableDatabase.execSQL("UPDATE prepared_bale SET dispatchCycle=2 WHERE barcode=?", arrayOf(f.barcodes[0]))
        f.processor.drain(f.assignment)
        assertEquals(1L, f.db.captures().get(c.id)!!.context.expectedDispatchCycle)
        assertEquals("INVALID", f.db.captures().get(c.id)!!.state); assertNull(f.db.scans().get(c.id))
    }

    @Test fun finalValidationRejectsChangedAvailabilityWithoutWritingSuccess() = scenario { f ->
        val c = f.receive(); f.processor.reception.validate(c.id)
        f.db.openHelper.writableDatabase.execSQL("UPDATE prepared_bale SET available=0 WHERE barcode=?", arrayOf(f.barcodes[0]))
        assertEquals("BUSINESS_REJECTED", f.processor.saving.save(c.id))
        assertNotNull(f.db.captures().get(c.id)!!.confirmedAt); assertNull(f.db.scans().get(c.id))
        assertNotNull(f.db.captures().failure(f.assignment))
    }
}
