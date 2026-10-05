package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.sync.RecoveryProtection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.IOException
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class HandoverContinuationGuardTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun unacknowledgedImportCannotPersistOrSendCommand() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverDownloadFixture(folder.root).use { f ->
            f.local.install()
            assertThrows(Exception::class.java) { HandoverContinuationRepository(f.local.db, f.connection).activate(f.local.caseId) }
            assertNull(f.local.db.handoverContinuations().get(f.local.caseId))
            assertEquals(0, f.server.requestCount)
        }
    }

    @Test fun tamperedSignedReceiptCannotThaw() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverContinuationFixture(folder.root).use { f ->
            f.receipt.put("nextOrderScanSeq", 999)
            rejected(f)
        }
    }

    @Test fun signedReceiptForAnotherCaseCannotThaw() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverContinuationFixture(folder.root).use { f ->
            f.receipt.put("caseId", UUID.randomUUID().toString()); f.resign()
            rejected(f)
        }
    }

    @Test fun receiptCannotReleaseStock() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverContinuationFixture(folder.root).use { f ->
            f.receipt.put("stockReleased", true); f.resign()
            rejected(f)
        }
    }

    @Test fun receiptCannotSubstituteAnotherImportAuthorization() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverContinuationFixture(folder.root).use { f ->
            f.receipt.put("previousAuthorizationId", UUID.randomUUID().toString()); f.resign()
            rejected(f)
        }
    }

    @Test fun receiptCannotExtendOriginalExpiry() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverContinuationFixture(folder.root).use { f ->
            f.receipt.getJSONObject("assignment").put("expiresAt", f.download.clock.plusSeconds(200000).toString())
            f.resign()
            rejected(f)
        }
    }

    @Test fun loginChangeAfterResponseKeepsOriginalQueueFrozen() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverContinuationFixture(folder.root).use { f ->
            f.download.afterResponse = { f.download.auth!!.put("localSessionId", UUID.randomUUID().toString()) }
            rejected(f)
        }
    }

    @Test fun permissionChangeAfterResponseKeepsOriginalQueueFrozen() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverContinuationFixture(folder.root).use { f ->
            f.download.afterResponse = { f.download.auth!!.put("userAuthVersion", "999") }
            rejected(f)
        }
    }

    @Test fun recoveryFenceAfterResponseKeepsOriginalQueueFrozen() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverContinuationFixture(folder.root).use { f ->
            f.download.afterResponse = { RecoveryProtection(f.local.db).observe(JSONObject()
                .put("serverRecoveryEpoch", "1").put("serviceMode", "RESTORING"), "1") }
            rejected(f)
        }
    }

    @Test fun changedIdentityCannotReplayPendingCommandAfterRestart() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverContinuationFixture(folder.root).use { f ->
            f.download.afterResponse = { throw IOException("lost") }
            rejected(f)
            val row = f.local.db.handoverContinuations().get(f.local.caseId)
            f.local.reopen()
            f.download.afterResponse = {}
            f.download.auth!!.put("userId", UUID.randomUUID().toString())
            rejected(f)
            assertEquals(1, f.bodies.size)
            assertEquals(row, f.local.db.handoverContinuations().get(f.local.caseId))
        }
    }

    @Test fun corruptedImportedAuditAfterResponseCannotThaw() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverContinuationFixture(folder.root).use { f ->
            f.download.afterResponse = { f.local.db.openHelper.writableDatabase.execSQL("DELETE FROM work_event") }
            rejected(f)
        }
    }

    private fun rejected(f: HandoverContinuationFixture) {
        assertThrows(Exception::class.java) { f.activate() }
        assertEquals("HANDOVER_FROZEN", f.local.db.work().state(f.local.target))
        assertNull(f.local.db.continuations().current(f.local.target))
        assertNull(f.local.db.handoverContinuations().get(f.local.caseId)?.continuationId)
    }
}
