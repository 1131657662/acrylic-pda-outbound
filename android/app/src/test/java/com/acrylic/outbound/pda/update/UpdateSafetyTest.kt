package com.acrylic.outbound.pda.update

import com.acrylic.outbound.pda.work.*
import com.acrylic.outbound.pda.submission.SubmissionFreeze
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class UpdateSafetyTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun persistedManualInputBlocksInstallAfterProcessReopen() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            f.receive(source = "MANUAL"); f.reload()
            assertTrue(UpdateSafety(f.db) { WorkUi() }.installBlockReason()!!.contains("未处理输入"))
        }
    }

    @Test fun durableFrozenSubmissionAllowsUpgradeWithoutDeletingOriginalRequest() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            f.scan(); val original = SubmissionFreeze(f.store) { f.now }.freeze(f.assignment, "短装测试")
            f.reload()
            assertNull(UpdateSafety(f.db) { WorkUi() }.installBlockReason())
            assertEquals(original, f.db.submissions().get(original.id))
        }
    }

    @Test fun unknownUploadOutcomeKeepsInstallationBlocked() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            f.scan(); val original = SubmissionFreeze(f.store) { f.now }.freeze(f.assignment, "短装测试")
            f.db.submissions().progress(original.id, "RESULT_UNKNOWN", null, "连接中断", true)
            assertTrue(UpdateSafety(f.db) { WorkUi() }.installBlockReason()!!.contains("原批次"))
            assertEquals(original.payload, f.db.submissions().get(original.id)!!.payload)
        }
    }

    @Test fun pauseForRecallReleasesScanLeaseOnceQueuedWorkCompletes() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            val engine = WorkEngine(f.store) {}
            try {
                engine.resume(f.assignment)
                withTimeout(10000) { while (!engine.state.value.ready) delay(10) }
                assertThrows(IllegalStateException::class.java) { UpdateGate.beginInstall() }
                engine.pauseForRecall(f.assignment)
                withTimeout(10000) { while (engine.state.value.queued > 0) delay(10) }
                UpdateGate.beginInstall()
                assertFalse(engine.receive(f.barcodes[0], "BROADCAST"))
            } finally { UpdateGate.endInstall(); engine.close() }
        }
    }
}
