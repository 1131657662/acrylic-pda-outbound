package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.network.ApiFailure
import com.acrylic.outbound.pda.network.SessionChanged
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
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class HandoverDownloadAcknowledgementTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun failedConfirmationKeepsFrozenImportAndReplaysOriginalBodyAfterReopen() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverDownloadFixture(folder.root).use { f ->
            f.failAck = true
            assertThrows(ApiFailure::class.java) { f.import() }
            val prepared = checkNotNull(f.local.db.preparations().order(f.local.target))
            val frozen = checkNotNull(f.local.db.work().order(f.local.target))
            assertEquals("HANDOVER_FROZEN", frozen.state)
            assertNull(prepared.ackAt)
            assertEquals(prepared.activatedAt, JSONObject(f.ackBodies.single()).getString("activatedAt"))

            f.failAck = false
            f.clock = f.clock.plusSeconds(20)
            f.local.reopen()
            val confirmed = f.ack()

            assertEquals(2, f.ackBodies.size)
            assertEquals(f.ackBodies[0], f.ackBodies[1])
            assertEquals(prepared, confirmed.copy(ackAt = null))
            assertEquals(frozen, f.local.db.work().order(f.local.target))
            assertEquals("IMPORTED_FROZEN", f.local.db.handoverStages().get(f.local.caseId)?.state)
            assertEquals(7, f.server.requestCount)
            assertEquals(confirmed, f.ack())
            assertEquals(7, f.server.requestCount)
        }
    }

    @Test fun unreadableResponseAfterRemoteReceiptCanBeRecoveredWithoutRedownload() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverDownloadFixture(folder.root).use { f ->
            f.local.install()
            f.malformedAck = true
            assertThrows(Exception::class.java) { f.ack() }
            assertNull(f.local.db.preparations().order(f.local.target)?.ackAt)

            f.malformedAck = false
            f.local.reopen()
            assertNotNull(f.ack().ackAt)
            assertEquals(2, f.server.requestCount)
            assertEquals(f.ackBodies[0], f.ackBodies[1])
        }
    }

    @Test fun stagedButNotImportedCannotSendConfirmation() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverDownloadFixture(folder.root).use { f ->
            assertThrows(IllegalStateException::class.java) { f.ack() }
            f.assertNoImport()
            assertEquals(0, f.server.requestCount)
        }
    }

    @Test fun mismatchedReceiptDoesNotMarkLocalConfirmation() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverDownloadFixture(folder.root).use { f ->
            f.local.install()
            f.ackAssignment = UUID.randomUUID().toString()
            assertThrows(IllegalStateException::class.java) { f.ack() }
            assertNull(f.local.db.preparations().order(f.local.target)?.ackAt)
            assertEquals("HANDOVER_FROZEN", f.local.db.work().order(f.local.target)?.state)
        }
    }

    @Test fun loginChangeAfterReceiptCannotRecordLocalConfirmation() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverDownloadFixture(folder.root).use { f ->
            f.local.install()
            f.afterResponse = { f.auth = JSONObject(f.auth.toString()).put("localSessionId", UUID.randomUUID().toString()) }
            assertThrows(SessionChanged::class.java) { f.ack() }
            assertNull(f.local.db.preparations().order(f.local.target)?.ackAt)
        }
    }

    @Test fun revokedPermissionVersionAfterReceiptCannotRecordConfirmation() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverDownloadFixture(folder.root).use { f ->
            f.local.install()
            f.afterResponse = { f.auth = JSONObject(f.auth.toString()).put("userAuthVersion", "999") }
            assertThrows(IllegalStateException::class.java) { f.ack() }
            assertNull(f.local.db.preparations().order(f.local.target)?.ackAt)
        }
    }

    @Test fun recoveryFenceAfterReceiptPreventsLocalConfirmation() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverDownloadFixture(folder.root).use { f ->
            f.local.install()
            f.afterResponse = { RecoveryProtection(f.local.db).observe(JSONObject()
                .put("serverRecoveryEpoch", "1").put("serviceMode", "RESTORING"), "1") }
            assertThrows(IllegalStateException::class.java) { f.ack() }
            assertNull(f.local.db.preparations().order(f.local.target)?.ackAt)
        }
    }

    @Test fun localReceiptPersistenceFailureRetainsOriginalRetryBody() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverDownloadFixture(folder.root).use { f ->
            f.local.install()
            f.local.db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER fail_handover_ack BEFORE UPDATE ON prepared_order
                BEGIN SELECT RAISE(ABORT, 'test confirmation persistence failure'); END""")
            assertThrows(Exception::class.java) { f.ack() }
            assertNull(f.local.db.preparations().order(f.local.target)?.ackAt)

            f.local.db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_handover_ack")
            f.local.reopen()
            assertNotNull(f.ack().ackAt)
            assertEquals(f.ackBodies[0], f.ackBodies[1])
        }
    }

    @Test fun corruptedImportedFactsCannotSendConfirmation() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverDownloadFixture(folder.root).use { f ->
            f.local.install()
            f.local.db.openHelper.writableDatabase.execSQL("DELETE FROM work_event")
            assertThrows(IllegalStateException::class.java) { f.ack() }
            assertEquals(0, f.server.requestCount)
            assertNull(f.local.db.preparations().order(f.local.target)?.ackAt)
        }
    }
}
