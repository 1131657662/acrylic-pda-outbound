package com.acrylic.outbound.pda.retention

import com.acrylic.outbound.pda.data.LocalRecoveryControl
import com.acrylic.outbound.pda.network.ApiFailure
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.IOException

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class SubmissionArchiveTest {
    @Test fun signedServerContractIsPersistedAcrossRestartWithoutRewritingBusinessFacts() = runBlocking<Unit>(Dispatchers.IO) {
        ArchiveFixture().use { f ->
            val order = f.db.work().order(f.local.assignmentId)
            assertTrue(f.repository.refresh().contains("已核对")); assertEquals(1, f.calls)
            val saved = checkNotNull(f.saved()); assertEquals(f.now.toEpochMilli(), saved.confirmedAt)
            f.reopen(); assertEquals(saved, f.saved()); f.repository.refresh(); assertEquals(1, f.calls)
            assertEquals(f.local, f.db.submissions().get(f.local.id)); assertEquals(order, f.db.work().order(f.local.assignmentId))
        }
    }
    @Test fun lostResponseRetriesSameHashesWithoutUndoingSuccessfulSubmission() = runBlocking<Unit>(Dispatchers.IO) {
        ArchiveFixture().use { f ->
            f.respond = { throw IOException("模拟确认响应丢失") }
            assertTrue(f.repository.refresh().contains("已保留")); assertNull(f.saved()); assertEquals(f.local, f.db.submissions().get(f.local.id))
            f.reopen(); f.respond = { f.proof }; f.repository.refresh()
            assertNotNull(f.saved()); assertEquals(2, f.calls)
        }
    }
    @Test fun unsignedResponseChangesNeverPersistConfirmation() = runBlocking<Unit>(Dispatchers.IO) {
        ArchiveFixture().use { f ->
            f.respond = { JSONObject(f.proof.toString()).put("localDeletionAllowed", true) }
            assertTrue(f.repository.refresh().contains("尚未核验")); assertNull(f.saved())
            assertEquals(f.local, f.db.submissions().get(f.local.id))
        }
    }
    @Test fun originalReceiptMustStillVerifyBeforeContactingServer() = runBlocking<Unit>(Dispatchers.IO) {
        ArchiveFixture().use { f ->
            f.db.submissions().progress(f.local.id, "SYNCED", JSONObject(f.receipt.toString()).put("actualCount", "1").toString(), "", false)
            f.repository.refresh(); assertEquals(0, f.calls); assertNull(f.saved())
        }
    }
    @Test fun unresolvedAndForeignIdentityRecordsAreExcluded() = runBlocking<Unit>(Dispatchers.IO) {
        ArchiveFixture().use { f ->
            for (state in listOf("FROZEN", "RESULT_UNKNOWN", "ACTION_REQUIRED", "REVIEW_REQUIRED", "ABORTED", "REJECTED")) {
                f.db.submissions().progress(f.local.id, state, f.local.receipt, "", false); f.repository.refresh()
            }
            f.db.submissions().progress(f.local.id, "SYNCED", f.local.receipt, "", false)
            f.auth.put("userId", "other-user"); f.repository.refresh()
            f.auth.put("userId", f.proof.getString("operatorId")).put("deviceId", "other-device"); f.repository.refresh()
            assertEquals(0, f.calls); assertNull(f.saved())
        }
    }
    @Test fun recoveryBarrierBeforeOrDuringRequestPreventsSaving() = runBlocking<Unit>(Dispatchers.IO) {
        ArchiveFixture().use { f ->
            f.db.sync().saveRecovery(LocalRecoveryControl(observedServerEpoch = "1", mode = "RECONCILING"))
            f.repository.refresh(); assertEquals(0, f.calls)
            f.db.sync().saveRecovery(LocalRecoveryControl(observedServerEpoch = "1", mode = "NORMAL"))
            f.respond = { f.db.sync().saveRecovery(LocalRecoveryControl(observedServerEpoch = "2", mode = "RECONCILING")); f.proof }
            f.repository.refresh(); assertEquals(1, f.calls); assertNull(f.saved())
        }
    }
    @Test fun sessionSwitchDuringRequestPreventsSaving() = runBlocking<Unit>(Dispatchers.IO) {
        ArchiveFixture().use { f ->
            f.respond = { f.auth.put("userId", "other"); f.proof }
            f.repository.refresh(); assertNull(f.saved()); assertEquals(f.local, f.db.submissions().get(f.local.id))
        }
    }
    @Test fun localStateChangeDuringRequestDoesNotConfirmOldSnapshot() = runBlocking<Unit>(Dispatchers.IO) {
        ArchiveFixture().use { f ->
            f.respond = { f.db.submissions().progress(f.local.id, "ACTION_REQUIRED", f.local.receipt, "需核对", false); f.proof }
            f.repository.refresh(); assertNull(f.saved()); assertEquals("ACTION_REQUIRED", f.db.submissions().get(f.local.id)!!.state)
        }
    }
    @Test fun missingEndpointAndMissingServerSubmissionHaveDistinctMessages() = runBlocking<Unit>(Dispatchers.IO) {
        ArchiveFixture().use { f ->
            f.respond = { throw ApiFailure("NOT_FOUND", "无接口", httpStatus = 404) }
            assertTrue(f.repository.refresh().contains("尚未提供"))
            f.respond = { throw ApiFailure("SUBMISSION_NOT_FOUND", "原批次不存在", httpStatus = 404) }
            assertTrue(f.repository.refresh().contains("尚未核验")); assertNull(f.saved())
        }
    }
    @Test fun failedLocalCommitKeepsPendingConfirmationForRetry() = runBlocking<Unit>(Dispatchers.IO) {
        ArchiveFixture().use { f ->
            f.db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER archive_fail BEFORE INSERT ON local_submission_archive
                BEGIN SELECT RAISE(ABORT,'simulated durable write failure'); END""")
            f.repository.refresh(); assertNull(f.saved()); assertEquals(f.local, f.db.submissions().get(f.local.id))
            f.db.openHelper.writableDatabase.execSQL("DROP TRIGGER archive_fail")
            f.repository.refresh(); assertNotNull(f.saved()); assertEquals(2, f.calls)
        }
    }
}
