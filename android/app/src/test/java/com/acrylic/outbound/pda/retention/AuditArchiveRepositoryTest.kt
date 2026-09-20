package com.acrylic.outbound.pda.retention

import com.acrylic.outbound.pda.data.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.IOException

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AuditArchiveRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun lostResponseAndRestartRetryOriginalRangeBeforeNewEvents() = runBlocking<Unit>(Dispatchers.IO) {
        AuditArchiveFixture(folder.root).use { f ->
            f.response = { _, _, _ -> throw IOException("lost response") }
            assertThrows(IOException::class.java) { f.repository.archive() }
            val pending = f.saved().single(); assertNull(pending.proofJson)
            f.work.reload(); f.event(); f.acknowledge(); f.response = f::answer
            f.repository.archive()
            assertEquals(f.calls[0], f.calls[1]); assertEquals(listOf(1L, 2L), f.saved().map { it.throughSeq })
            assertTrue(f.saved().all { it.proofJson != null }); assertEquals(2, f.db.work().deviceEvents(f.device).size)
        }
    }

    @Test fun invalidSignedLengthKeepsPendingRequestAndOriginalEvidence() = runBlocking<Unit>(Dispatchers.IO) {
        AuditArchiveFixture(folder.root).use { f ->
            f.response = { restore, body, epoch -> f.signer.sign(f.answer(restore, body, epoch).put("byteCount", "1")) }
            assertThrows(IllegalStateException::class.java) { f.repository.archive() }
            assertNull(f.saved().single().proofJson); assertEquals(1, f.db.work().deviceEvents(f.device).size)
        }
    }

    @Test fun identityChangeDuringNetworkNeverCommitsProof() = runBlocking<Unit>(Dispatchers.IO) {
        AuditArchiveFixture(folder.root).use { f ->
            f.response = { restore, body, epoch -> f.answer(restore, body, epoch).also { f.auth.put("userId", "changed") } }
            assertThrows(IllegalStateException::class.java) { f.repository.archive() }
            assertNull(f.saved().single().proofJson)
        }
    }

    @Test fun localWriteFailureRollsBackProofAndRetriesOriginalRequest() = runBlocking<Unit>(Dispatchers.IO) {
        AuditArchiveFixture(folder.root).use { f ->
            f.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_archive BEFORE UPDATE ON local_audit_archive BEGIN SELECT RAISE(ABORT,'test fault'); END")
            assertThrows(Exception::class.java) { f.repository.archive() }
            val pending = f.saved().single(); assertNull(pending.proofJson)
            f.db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_archive")
            f.repository.archive(); assertNotNull(f.saved().single().proofJson); assertEquals(f.calls[0], f.calls[1])
        }
    }

    @Test fun missingLocalEventCannotBeArchivedByAcknowledgementAlone() = runBlocking<Unit>(Dispatchers.IO) {
        AuditArchiveFixture(folder.root).use { f ->
            f.event(); f.acknowledge(); f.db.openHelper.writableDatabase.execSQL("DELETE FROM work_event WHERE localSeq=1")
            assertThrows(IllegalStateException::class.java) { f.repository.archive() }
            assertTrue(f.saved().isEmpty()); assertTrue(f.calls.isEmpty())
        }
    }

    @Test fun restoredPrefixSeedsOnlyCurrentEpochAndKeepsReconciliationBarrier() = runBlocking<Unit>(Dispatchers.IO) {
        AuditArchiveFixture(folder.root).use { f ->
            f.repository.archive(); val old = f.saved().single(); f.epoch("2")
            f.db.openHelper.writableDatabase.execSQL("DELETE FROM work_event")
            assertTrue(f.repository.restore())
            assertEquals(1L, f.db.audit().checkpoint("2", f.device)!!.acknowledgedThrough)
            assertEquals(old, f.saved("1").single()); assertEquals("RECONCILING", f.db.sync().recovery()!!.mode)
            assertEquals("2", JSONObject(f.saved().single().proofJson!!).getString("recoveryEpoch"))
        }
    }

    @Test fun restorationNeverReplacesAnInflightAuditRequest() = runBlocking<Unit>(Dispatchers.IO) {
        AuditArchiveFixture(folder.root).use { f ->
            f.repository.archive(); f.epoch("2")
            val pending = AuditCheckpoint("2", f.device, pendingJson = "original request")
            f.db.audit().save(pending); assertTrue(f.repository.restore())
            assertEquals(pending, f.db.audit().checkpoint("2", f.device))
        }
    }

    @Test fun successiveEpochChangesResumeLongestOriginalPrefixInBoundedBatches() = runBlocking<Unit>(Dispatchers.IO) {
        AuditArchiveFixture(folder.root).use { f ->
            repeat(20) { f.event() }
            for (i in 0L..20L) f.db.auditArchives().insert(f.signer.row(f.signer.proof(f.payload(i, i + 1), f.warehouse)))
            f.epoch("2"); assertFalse(f.repository.restore()); assertEquals(20, f.saved().size)
            f.epoch("3"); val before = f.calls.size
            assertFalse(f.repository.restore()); assertEquals(20, f.calls.size - before)
            assertTrue(f.repository.restore()); assertEquals(21L, f.db.audit().checkpoint("3", f.device)!!.acknowledgedThrough)
            assertEquals(21, f.saved("1").size); assertEquals("RECONCILING", f.db.sync().recovery()!!.mode)
        }
    }

    @Test fun repeatedRestoreAcceptsCurrentPrefixLongerThanHistoricalOne() = runBlocking<Unit>(Dispatchers.IO) {
        AuditArchiveFixture(folder.root).use { f ->
            f.repository.archive(); f.epoch("2"); assertTrue(f.repository.restore())
            f.event(); f.acknowledge(); f.repository.archive()
            val before = f.calls.size; assertTrue(f.repository.restore()); assertEquals(before, f.calls.size)
            assertEquals(2L, f.saved().last().throughSeq)
        }
    }
}
