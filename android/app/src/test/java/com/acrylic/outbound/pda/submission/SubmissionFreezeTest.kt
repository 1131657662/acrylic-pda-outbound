package com.acrylic.outbound.pda.submission

import com.acrylic.outbound.pda.work.WorkFixture
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class SubmissionFreezeTest {
    @get:Rule val folder = TemporaryFolder()
    private fun scenario(block: (WorkFixture) -> Unit) = runBlocking<Unit>(Dispatchers.IO) { WorkFixture(folder.root).use(block) }
    @Test fun frozenBodySurvivesReopenAndCannotBeChangedByRetry() = scenario { f ->
        f.scan(); val freeze = SubmissionFreeze(f.store) { f.now }; val first = freeze.freeze(f.assignment, "实物不足，短装申请")
        f.reload(); val retry = SubmissionFreeze(f.store) { f.now.plusSeconds(60) }.freeze(f.assignment, "另一个原因")
        assertEquals(first, retry); assertEquals("FROZEN", f.db.work().order(f.assignment)!!.state)
        assertThrows(IllegalStateException::class.java) { f.scan(f.barcodes[1]) }
        assertThrows(IllegalStateException::class.java) { f.processor.corrections.voidLast(f.assignment, f.db.scans().lastActive(f.assignment)!!.captureId, "不可撤销") }
    }
    @Test fun zeroCountPendingInputAndMissingShortReasonCannotFreeze() = scenario { f ->
        val freeze = SubmissionFreeze(f.store) { f.now }
        assertThrows(IllegalStateException::class.java) { freeze.freeze(f.assignment, "空单") }
        f.scan(); assertThrows(IllegalStateException::class.java) { freeze.freeze(f.assignment, "") }
        f.receive(f.barcodes[1], "MANUAL")
        assertThrows(IllegalStateException::class.java) { freeze.freeze(f.assignment, "短装") }
        assertNull(f.db.submissions().forOrder(f.assignment)); assertEquals("SCANNING", f.db.work().order(f.assignment)!!.state)
    }
    @Test fun expiryStopsScanningButAllowsFreezingExistingEvidence() = scenario { f ->
        f.scan(); f.now = f.now.plusSeconds(49 * 3600)
        assertThrows(IllegalStateException::class.java) { f.scan(f.barcodes[1]) }
        val frozen = SubmissionFreeze(f.store) { f.now }.freeze(f.assignment, "到期上传既有记录")
        assertEquals("1", JSONObject(frozen.payload).getString("expectedCount"))
    }
    @Test fun freezeWriteFailureRollsBackFrozenEventAndLeavesWorkRecoverable() = scenario { f ->
        f.scan(); val events = f.db.work().events(f.assignment)
        f.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER stop_freeze BEFORE INSERT ON local_submission BEGIN SELECT RAISE(ABORT, 'full'); END")
        assertThrows(Exception::class.java) { SubmissionFreeze(f.store) { f.now }.freeze(f.assignment, "短装") }
        assertEquals(events, f.db.work().events(f.assignment)); assertEquals("SCANNING", f.db.work().order(f.assignment)!!.state)
        f.db.openHelper.writableDatabase.execSQL("DROP TRIGGER stop_freeze")
        assertEquals("FROZEN", SubmissionFreeze(f.store) { f.now }.freeze(f.assignment, "短装").state)
    }
    @Test fun failuresAndVoidsRemainInFrozenEvidenceWithOriginalSequenceGaps() = scenario { f ->
        val bad = f.scan("bad"); f.processor.corrections.acknowledge(bad.id)
        val saved = f.scan(); f.processor.corrections.voidLast(f.assignment, saved.id, "实物未装车")
        f.scan(); val frozen = JSONObject(SubmissionFreeze(f.store) { f.now }.freeze(f.assignment, "短装").payload)
        assertEquals("3", frozen.getString("captureHighWatermark")); assertEquals("1", frozen.getJSONObject("captureSummary").getString("voided"))
        assertEquals("3", frozen.getJSONArray("scans").getJSONObject(1).getString("orderScanSeq")); assertEquals(3, frozen.getJSONArray("captures").length())
    }
}
