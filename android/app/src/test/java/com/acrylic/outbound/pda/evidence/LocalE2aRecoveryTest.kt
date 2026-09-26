package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.temporary.*
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
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LocalE2aRecoveryTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun lostReceiptAtEveryPhaseRecoversOriginalKeysAfterReopen() = runBlocking<Unit>(Dispatchers.IO) {
        for (phase in listOf("OPEN", "RAW", "PAGE", "STOP", "VERIFY")) frozen { f, transport ->
            val original = transport.all()
            val repo = transport.repository(); repo.queue(f.id); transport.drop = phase
            assertThrows(IOException::class.java) { finish(repo, f.id) }
            assertEquals("RESULT_UNKNOWN", f.db.localE2aReceipts().queue(f.id)?.state)
            f.reopen(); f.fixture.stopSigner.unavailable = true
            finish(transport.repository(), f.id)
            assertEquals(original, transport.all())
            assertEquals("PARTIAL", f.db.localE2aReceipts().queue(f.id)?.state)
            assertFalse(checkNotNull(f.db.localE2aReceipts().queue(f.id)).autoRetry)
            assertTrue(transport.posts.all { it in original })
            assertEquals(original.size + if (phase == "RAW") 1 else 0, transport.posts.size)
            transport.received.forEach { (key, bytes) -> assertArrayEquals(bytes, f.db.localE2aReceipts().get(key)?.responseBytes) }
            assertEquals(1, f.fixture.stopSigner.calls)
        }
    }

    @Test fun wrongSignedWarehouseIsRetainedUntrustedAndStopsTheChain() = runBlocking<Unit>(Dispatchers.IO) {
        frozen { f, transport ->
            transport.signer.change = { it.getJSONObject("originalContext").put("warehouseId", java.util.UUID.randomUUID().toString()) }
            val repo = transport.repository(); repo.queue(f.id)
            assertThrows(IllegalStateException::class.java) { repo.resume(f.id) }
            val open = transport.all().first()
            assertEquals("RECEIVED", f.db.localE2aReceipts().get(open.originalKey)?.state)
            assertArrayEquals(transport.received[open.originalKey], f.db.localE2aReceipts().get(open.originalKey)?.responseBytes)
            assertEquals("ACTION_REQUIRED", f.db.localE2aReceipts().queue(f.id)?.state)
            f.reopen(); assertThrows(IllegalStateException::class.java) { transport.repository().resume(f.id) }
            assertEquals(1, transport.posts.size)
        }
    }

    @Test fun modifiedDurableReceiptCannotSkipToTheNextPhase() = runBlocking<Unit>(Dispatchers.IO) {
        frozen { f, transport ->
            val repo = transport.repository(); repo.queue(f.id); repo.resume(f.id, SyncRun(batches = 1))
            val key = transport.all().first().originalKey
            val saved = checkNotNull(f.db.localE2aReceipts().get(key))
            f.db.localE2aReceipts().update(saved.copy(responseBytes = "{}".toByteArray()))
            assertThrows(IllegalStateException::class.java) { repo.resume(f.id) }
            assertEquals(1, transport.posts.size)
            assertFalse(checkNotNull(f.db.localE2aReceipts().queue(f.id)).autoRetry)
        }
    }

    @Test fun changedSessionAfterResponseCannotAdvanceAndOriginalKeyRemainsRecoverable() = runBlocking<Unit>(Dispatchers.IO) {
        frozen { f, transport ->
            val repo = transport.repository(); repo.queue(f.id)
            val auth = checkNotNull(f.fixture.auth); val generation = auth.getString("localSessionId")
            transport.beforeResponse = { auth.put("localSessionId", java.util.UUID.randomUUID().toString()) }
            assertThrows(IllegalStateException::class.java) { repo.resume(f.id) }
            assertEquals("RESULT_UNKNOWN", f.db.localE2aReceipts().get(transport.all().first().originalKey)?.state)
            transport.beforeResponse = {}; auth.put("localSessionId", generation)
            repo.queue(f.id); finish(repo, f.id)
            assertEquals(transport.all().size, transport.posts.size)
        }
    }

    @Test fun verifyWaitsForTheOriginalStopAuditPrefixBeforeSending() = runBlocking<Unit>(Dispatchers.IO) {
        frozen { f, transport ->
            transport.auditComplete = false
            val repo = transport.repository(); repo.queue(f.id)
            repo.resume(f.id)
            assertFalse(transport.posts.any { it.operation == "VERIFY" })
            assertTrue(checkNotNull(f.db.localE2aReceipts().queue(f.id)).autoRetry)
            assertNull(f.db.localE2aReceipts().get(transport.plan().verifyOriginalKey))
            transport.auditComplete = true
            finish(repo, f.id)
            assertEquals(1, transport.posts.count { it.operation == "VERIFY" })
            assertEquals(2, transport.auditCalls)
        }
    }

    @Test fun missingAuditPermissionCannotBeBypassedByRecoveryPermission() = runBlocking<Unit>(Dispatchers.IO) {
        frozen { f, transport ->
            checkNotNull(f.fixture.auth).getJSONObject("capabilities").put("auditUpload", false)
            val repo = transport.repository(); repo.queue(f.id)
            assertThrows(IllegalStateException::class.java) { repo.resume(f.id) }
            assertFalse(transport.posts.any { it.operation == "VERIFY" })
            assertEquals(0, transport.auditCalls)
            assertEquals("ACTION_REQUIRED", f.db.localE2aReceipts().queue(f.id)?.state)
        }
    }

    private fun frozen(action: (TemporaryWorkFixture, E2aRecoveryFixture) -> Unit) {
        TemporaryWorkFixture(folder.newFolder()).use { f ->
            f.start(); f.scan(); TemporaryFreeze(f.store).freeze(f.id, "短装待核对")
            action(f, E2aRecoveryFixture(f))
        }
    }
    private fun finish(repo: LocalE2aRecoveryRepository, id: String) {
        repeat(20) { if (repo.resume(id).state == "PARTIAL") return }
        fail("Recovery did not finish within the bounded fixture")
    }
}
