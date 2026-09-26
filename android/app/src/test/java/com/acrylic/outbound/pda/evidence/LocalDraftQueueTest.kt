package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.temporary.TemporaryWorkFixture
import com.acrylic.outbound.pda.sync.SyncRun
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LocalDraftQueueTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun corruptStopCannotEnableAutomaticTransfer() = damaged(
        "UPDATE local_draft_stop_item SET rawBytes=X'7b7d' WHERE ordinal=1")

    @Test fun changedRequestCannotEnableAutomaticTransfer() = damaged(
        "UPDATE local_draft_request SET requestJson='{}' WHERE position=0")

    private fun damaged(sql: String) = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            LocalDraftStopRepository(f.store).stop(f.id)
            val wire = LocalDraftRecoveryFixture(f); wire.repository().prepare(f.id)
            f.db.openHelper.writableDatabase.execSQL(sql)
            assertThrows(Exception::class.java) { wire.repository().prepare(f.id) }
            f.reopen(); assertFalse(checkNotNull(f.db.localDraftQueue().get(f.id)).autoRetry)
            assertTrue(wire.posts.isEmpty()); assertEquals(1, f.fixture.stopSigner.calls)
            assertEquals("EVIDENCE_STOPPED", f.db.temporaryDrafts().get(f.id)!!.state)
        }
    }

    @Test fun invalidStoredReceiptCannotBeReenabledOrOverwritten() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            LocalDraftStopRepository(f.store).stop(f.id)
            val wire = LocalDraftRecoveryFixture(f); wire.repository().prepare(f.id)
            wire.signer.change = { it.put("releaseAllowed", true) }
            assertThrows(Exception::class.java) { wire.repository().resume(f.id) }
            val request = wire.all().first(); val raw = f.db.localDraftTransfers().receipt(request.originalKey)!!.responseBytes
            f.reopen(); wire.signer.change = {}
            assertThrows(Exception::class.java) { wire.repository().prepare(f.id) }
            assertFalse(f.db.localDraftQueue().get(f.id)!!.autoRetry)
            assertArrayEquals(raw, f.db.localDraftTransfers().receipt(request.originalKey)!!.responseBytes)
            assertEquals(1, wire.posts.size)
        }
    }

    @Test fun laterInvalidReceiptBlocksNetworkBeforeSendingEarlierUnknownRequest() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            LocalDraftStopRepository(f.store).stop(f.id)
            val wire = LocalDraftRecoveryFixture(f); wire.repository().prepare(f.id)
            val request = wire.all().last()
            f.db.localDraftTransfers().insertReceipt(LocalDraftReceipt(request.originalKey, "RECEIVED", "{}".toByteArray()))
            assertThrows(Exception::class.java) { wire.repository().resume(f.id) }
            assertTrue(wire.reads.isEmpty()); assertTrue(wire.posts.isEmpty())
            assertFalse(f.db.localDraftQueue().get(f.id)!!.autoRetry)
        }
    }

    @Test fun pageInspectionNeverSendsOrEnablesPausedTransfer() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            LocalDraftStopRepository(f.store).stop(f.id)
            val wire = LocalDraftRecoveryFixture(f); val plan = wire.repository().prepare(f.id)
            wire.repository().resume(f.id, SyncRun(batches = 1))
            f.db.localDraftQueue().pauseAll("明确暂停")
            val before = f.db.localDraftQueue().get(f.id); val reads = wire.reads.size
            val inspected = wire.repository().checked(f.id)
            assertEquals(1L, inspected.verifiedRequests); assertEquals(plan.requestCount, inspected.totalRequests)
            assertEquals(before, f.db.localDraftQueue().get(f.id)); assertEquals(reads, wire.reads.size)
            assertThrows(Exception::class.java) { wire.repository().resume(f.id) }
            assertEquals(reads, wire.reads.size)
        }
    }

    @Test fun completeTagWithoutOriginalReceiptsCannotTriggerReadOrPost() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            LocalDraftStopRepository(f.store).stop(f.id)
            val wire = LocalDraftRecoveryFixture(f); wire.repository().prepare(f.id)
            val row = checkNotNull(f.db.localDraftQueue().get(f.id))
            f.db.localDraftQueue().update(row.copy(state = "ORIGINALS_STORED", autoRetry = false))
            assertThrows(Exception::class.java) { wire.repository().checked(f.id) }
            assertTrue(wire.reads.isEmpty()); assertTrue(wire.posts.isEmpty())
        }
    }

    @Test fun pauseAfterResponseStopsFollowingRequestsWithoutDiscardingOriginal() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            LocalDraftStopRepository(f.store).stop(f.id)
            val wire = LocalDraftRecoveryFixture(f); wire.repository().prepare(f.id)
            wire.beforeResponse = { f.db.localDraftQueue().pauseAll("身份切换先提交停止") }
            assertThrows(Exception::class.java) { wire.repository().resume(f.id) }
            assertEquals(1, wire.posts.size); assertEquals(1, wire.reads.size)
            val first = wire.all().first(); val original = f.db.localDraftTransfers().receipt(first.originalKey)!!
            assertArrayEquals(wire.stored[first.originalKey], original.responseBytes)
            assertFalse(f.db.localDraftQueue().get(f.id)!!.autoRetry)
            f.reopen(); assertThrows(Exception::class.java) { wire.repository().resume(f.id) }
            assertEquals(1, wire.posts.size)
        }
    }
}
