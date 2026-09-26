package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.network.ApiFailure
import com.acrylic.outbound.pda.sync.SyncRun
import com.acrylic.outbound.pda.temporary.TemporaryWorkFixture
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LocalDraftRecoveryTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun droppedOpenItemVerifyRecoverAfterReopenWithoutRepostingOrResigning() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.scan(); f.receive(); LocalDraftStopRepository(f.store).stop(f.id)
            val wire = LocalDraftRecoveryFixture(f); val plan = wire.repository().prepare(f.id); val originals = wire.all()
            for (operation in listOf("OPEN", "ITEM", "VERIFY")) {
                wire.drop = operation
                assertThrows(IOException::class.java) { wire.repository().resume(f.id) }
                f.fixture.stopSigner.unavailable = true; f.reopen()
                assertEquals(plan, wire.repository().prepare(f.id)); assertEquals(originals, wire.all())
            }
            assertEquals("ORIGINALS_STORED", wire.repository().resume(f.id).state)
            assertEquals(plan.requestCount, wire.posts.size.toLong()); assertEquals(plan.requestCount, wire.posts.map { it.originalKey }.toSet().size.toLong())
            f.reopen(); assertEquals("ORIGINALS_STORED", wire.repository().resume(f.id).state)
            assertEquals(plan.requestCount, wire.posts.size.toLong()); assertEquals(1, f.fixture.stopSigner.calls)
            wire.all().forEach { assertEquals("VERIFIED", f.db.localDraftTransfers().receipt(it.originalKey)!!.state) }
        }
    }
    @Test fun batchesResumeFromDurableReceipts() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            LocalDraftStopRepository(f.store).stop(f.id); val wire = LocalDraftRecoveryFixture(f); val plan = wire.repository().prepare(f.id)
            repeat(plan.requestCount.toInt()) { position ->
                val result = wire.repository().resume(f.id, SyncRun(batches = 1))
                assertEquals((position + 1).toLong(), result.verifiedRequests); f.reopen()
            }
            assertEquals("ORIGINALS_STORED", wire.repository().resume(f.id).state)
        }
    }
    @Test fun invalidSignedResponseIsDurableAndNeverOverwrittenByRetry() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            LocalDraftStopRepository(f.store).stop(f.id); val wire = LocalDraftRecoveryFixture(f); wire.repository().prepare(f.id)
            wire.signer.change = { it.put("localDeletionAllowed", true) }
            assertThrows(IllegalStateException::class.java) { wire.repository().resume(f.id) }
            val request = wire.all().first(); val saved = f.db.localDraftTransfers().receipt(request.originalKey)!!
            assertEquals("RECEIVED", saved.state); f.reopen(); wire.signer.change = {}
            assertThrows(IllegalStateException::class.java) { wire.repository().resume(f.id) }
            assertArrayEquals(saved.responseBytes, f.db.localDraftTransfers().receipt(request.originalKey)!!.responseBytes); assertEquals(1, wire.posts.size)
        }
    }
    @Test fun receivedButNotVerifiedResponseCanFinishAfterReopenWithoutNetwork() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            LocalDraftStopRepository(f.store).stop(f.id); val wire = LocalDraftRecoveryFixture(f); wire.repository().prepare(f.id)
            val row = wire.all().first(); val bytes = wire.signer.response(LocalDraftReceiptVerifier.Expected(wire.stop(), row, wire.counts()))
            f.db.runInTransaction { val receipts = LocalDraftReceiptStore(f.db, listOf(wire.signer.key)); receipts.pending(row); receipts.received(row, bytes) }
            f.reopen(); wire.repository().resume(f.id, SyncRun(batches = 1))
            assertEquals("VERIFIED", f.db.localDraftTransfers().receipt(row.originalKey)!!.state)
            assertFalse(wire.reads.contains(row.originalKey)); assertTrue(wire.posts.none { it.originalKey == row.originalKey })
        }
    }
    @Test fun changedIdentityAfterServerCommitPreservesUnknownThenRecoversOriginalKey() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            LocalDraftStopRepository(f.store).stop(f.id); val wire = LocalDraftRecoveryFixture(f); wire.repository().prepare(f.id)
            val auth = JSONObject(checkNotNull(f.fixture.auth).toString()); wire.beforeResponse = { f.fixture.auth = null }
            assertThrows(Exception::class.java) { wire.repository().resume(f.id) }
            assertEquals("RESULT_UNKNOWN", f.db.localDraftTransfers().receipt(wire.all().first().originalKey)!!.state)
            f.fixture.auth = auth; wire.beforeResponse = {}; f.reopen()
            assertFalse(f.db.localDraftQueue().get(f.id)!!.autoRetry)
            assertThrows(Exception::class.java) { wire.repository().resume(f.id) }
            wire.repository().prepare(f.id); wire.repository().resume(f.id)
            assertEquals(1, wire.posts.count { it.operation == "OPEN" })
        }
    }
    @Test fun unrelated404NeverTriggersNewPost() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            LocalDraftStopRepository(f.store).stop(f.id); val wire = LocalDraftRecoveryFixture(f); wire.repository().prepare(f.id)
            wire.failReads = ApiFailure("NOT_FOUND", "wrong endpoint", httpStatus = 404)
            assertThrows(ApiFailure::class.java) { wire.repository().resume(f.id) }; assertTrue(wire.posts.isEmpty())
        }
    }
}
