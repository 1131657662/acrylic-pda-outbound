package com.acrylic.outbound.pda.recall

import com.acrylic.outbound.pda.work.WorkFixture
import com.acrylic.outbound.pda.submission.SubmissionFreeze
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RecoveryFreezeTest {
    @get:Rule val folder = TemporaryFolder()
    private fun scenario(block: (WorkFixture) -> Unit) = runBlocking<Unit>(Dispatchers.IO) { WorkFixture(folder.root).use(block) }
    private fun stop(f: WorkFixture) {
        f.db.runInTransaction {
            f.db.recalls().insert(LocalRecall(UUID.randomUUID().toString(), f.assignment, "{}")); f.db.work().recall(f.assignment)
        }
    }
    private fun freeze(f: WorkFixture): LocalRecall {
        val freezer = RecoveryFreeze(f.store) { f.now }
        return freezer.freeze(f.assignment, freezer.fingerprint(freezer.unresolved(f.assignment)))
    }
    @Test fun zeroPackageSurvivesReopenWithStableBytesAndNoScanning() = scenario { f ->
        stop(f); val first = freeze(f); val body = JSONObject(first.payload!!)
        assertEquals("0", body.getString("expectedCount")); assertEquals("0", body.getString("captureHighWatermark"))
        f.reload(); assertEquals(first, freeze(f)); assertEquals("RECALL_PENDING", f.db.work().order(f.assignment)!!.state)
        assertThrows(IllegalStateException::class.java) { f.scan() }
        assertEquals("RECALL_STOPPED", body.getJSONArray("auditEvents").getJSONObject(body.getJSONArray("auditEvents").length() - 1).getString("type"))
    }
    @Test fun reviewedPendingAndFailedInputsKeepOriginalIdentityAndConfirmation() = scenario { f ->
        val invalid = f.scan("bad"); val pending = f.receive(); stop(f)
        assertThrows(IllegalStateException::class.java) { f.processor.reception.validate(pending.id) }
        val body = JSONObject(freeze(f).payload!!); val records = body.getJSONArray("captures")
        assertEquals(invalid.id, records.getJSONObject(0).getString("id")); assertEquals(pending.id, records.getJSONObject(1).getString("id"))
        assertEquals("BUSINESS_REJECTED", records.getJSONObject(1).getString("state"))
        assertEquals(pending.context, f.db.captures().get(pending.id)!!.context)
        assertNotNull(f.db.captures().get(pending.id)!!.failureAckAt); assertEquals(0, f.db.scans().total(f.assignment).count)
    }
    @Test fun staleUserReviewCannotSilentlyResolveNewInput() = scenario { f ->
        val freezer = RecoveryFreeze(f.store) { f.now }; val reviewed = freezer.fingerprint(emptyList())
        f.receive(); stop(f)
        assertThrows(IllegalStateException::class.java) { freezer.freeze(f.assignment, reviewed) }
        assertNull(f.db.recalls().get(f.assignment)!!.payload); assertEquals(1, f.db.captures().pendingCount(f.assignment))
    }
    @Test fun expiredAuthorizationStillPreservesLoadedBalesAndOldFrozenSubmission() = scenario { f ->
        f.scan(); val submission = SubmissionFreeze(f.store) { f.now }.freeze(f.assignment, "短装待审")
        val scans = f.db.scans().page(f.assignment, 0, Int.MAX_VALUE); stop(f); f.now = f.now.plusSeconds(49 * 3600)
        val body = JSONObject(freeze(f).payload!!)
        assertEquals("1", body.getString("expectedCount")); assertEquals(scans, f.db.scans().page(f.assignment, 0, Int.MAX_VALUE))
        assertEquals(submission, f.db.submissions().get(submission.id)); assertEquals(1, f.db.scans().total(f.assignment).count)
    }
    @Test fun storageFailureRollsBackReviewAndStopEventWithoutResuming() = scenario { f ->
        f.receive(); stop(f); val before = f.db.work().events(f.assignment)
        f.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_recovery BEFORE UPDATE ON local_recall BEGIN SELECT RAISE(ABORT, 'full'); END")
        assertThrows(Exception::class.java) { freeze(f) }
        assertEquals(before, f.db.work().events(f.assignment)); assertEquals(1, f.db.captures().pendingCount(f.assignment))
        assertEquals("RECALL_PENDING", f.db.work().order(f.assignment)!!.state)
    }
}
