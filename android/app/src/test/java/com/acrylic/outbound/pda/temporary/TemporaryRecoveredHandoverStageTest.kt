package com.acrylic.outbound.pda.temporary

import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.util.concurrent.Callable

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryRecoveredHandoverStageTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun recoveryDownloadPersistsOriginalBytesWithoutOpeningWork() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryRecoveredHandoverFixture(folder.root).use { f ->
            val row = f.repository.stage(f.caseId)
            f.local.reopen()
            assertEquals(row, f.repository.read(f.caseId))
            assertEquals(row, f.repository.stage(f.caseId))
            assertArrayEquals(f.base.bytes(f.body), f.db.temporaryRecoveredHandoverChunks().read(f.caseId, "PAYLOAD", row.payloadLength))
            assertEquals("1", row.sourceRecoveryEpoch); assertEquals("2", row.targetRecoveryEpoch)
            assertNull(f.db.temporaryHandoverStages().get(f.caseId))
            assertNull(f.db.temporaryHandoverConfirmations().get(f.caseId))
            assertNull(f.db.temporaryHandoverActivations().get(f.caseId))
            assertEquals("RECONCILING", f.db.sync().recovery()!!.mode)
        }
    }
    @Test fun failedPayloadInsertRollsBackWholeStage() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryRecoveredHandoverFixture(folder.root).use { f ->
            f.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_recovered_chunk BEFORE INSERT ON temporary_recovered_handover_chunk WHEN NEW.kind='PAYLOAD' BEGIN SELECT RAISE(ABORT,'synthetic disk write failure'); END")
            assertThrows(Exception::class.java) { f.repository.stage(f.caseId) }
            assertNull(f.db.temporaryRecoveredHandoverStages().get(f.caseId))
            f.db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM temporary_recovered_handover_chunk").use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
            }
        }
    }
    @Test fun changedDownloadSessionCannotCommit() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryRecoveredHandoverFixture(folder.root).use { f ->
            f.base.beforeOriginal = { if (it.endsWith("recovery-package")) f.session.put("localSessionId", java.util.UUID.randomUUID().toString()) }
            assertThrows(Exception::class.java) { f.repository.stage(f.caseId) }
            assertNull(f.db.temporaryRecoveredHandoverStages().get(f.caseId))
        }
    }
    @Test fun savedDamageBlocksReadingAndPreservesParent() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryRecoveredHandoverFixture(folder.root).use { f ->
            val row = f.repository.stage(f.caseId)
            f.db.openHelper.writableDatabase.execSQL("UPDATE temporary_recovered_handover_chunk SET bytes=? WHERE caseId=? AND kind='PAYLOAD'", arrayOf(byteArrayOf(1), f.caseId))
            assertThrows(Exception::class.java) { f.repository.read(f.caseId) }
            assertEquals(row, f.db.temporaryRecoveredHandoverStages().get(f.caseId))
        }
    }
    @Test fun commitTailRechecksReconciliationFence() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryRecoveredHandoverFixture(folder.root).use { f ->
            f.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER change_recovery_fence AFTER INSERT ON temporary_recovered_handover_chunk WHEN NEW.kind='PAYLOAD' BEGIN UPDATE local_recovery_control SET mode='NORMAL'; END")
            assertThrows(Exception::class.java) { f.repository.stage(f.caseId) }
            assertNull(f.db.temporaryRecoveredHandoverStages().get(f.caseId))
            assertEquals("RECONCILING", f.db.sync().recovery()!!.mode)
        }
    }
    @Test fun simultaneousSameCaseReturnsOneExactStage() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryRecoveredHandoverFixture(folder.root).use { f ->
            val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
            try {
                val results = pool.invokeAll(List(2) { Callable { f.repository.stage(f.caseId) } }).map { it.get() }
                assertEquals(results[0], results[1])
                assertEquals(results[0], f.repository.read(f.caseId))
            } finally { pool.shutdownNow() }
        }
    }
}
