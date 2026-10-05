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

    @Test fun completeStopReceiptRecoversAfterLossWithoutGrantingWorkOrRelease() = runBlocking<Unit>(Dispatchers.IO) {
        frozen { f, transport ->
            transport.signer.change = { if (it.getString("operationKind") == "VERIFY") it.put("status", "VERIFIED_COMPLETE_AT_STOP") }
            val repo = transport.repository(); repo.queue(f.id)
            val state = f.db.temporaryDrafts().get(f.id)!!.state
            val original = transport.all(); transport.drop = "VERIFY"
            assertThrows(IOException::class.java) { finish(repo, f.id) }
            f.reopen(); finish(transport.repository(), f.id)
            assertEquals(original, transport.all())
            assertEquals(state, f.db.temporaryDrafts().get(f.id)!!.state)
            assertFalse(f.db.localE2aReceipts().queue(f.id)!!.autoRetry)
            val verify = original.single { it.operation == "VERIFY" }
            val saved = f.db.localE2aReceipts().get(verify.originalKey)!!
            assertEquals("VERIFIED", saved.state)
            assertArrayEquals(transport.received[verify.originalKey], saved.responseBytes)
            assertEquals(original.size, transport.posts.size)
            val receipt = org.json.JSONObject(saved.responseBytes!!.toString(Charsets.UTF_8))
            assertEquals("VERIFIED_COMPLETE_AT_STOP", receipt.getString("status"))
            receipt.getJSONObject("capabilities").keys().forEach { assertEquals(false, receipt.getJSONObject("capabilities").get(it)) }
        }
    }

    @Test fun completeStatusIsInvalidForStopAndCannotEnableLaterVerification() = runBlocking<Unit>(Dispatchers.IO) {
        frozen { f, transport ->
            transport.signer.change = { if (it.getString("operationKind") == "STOP") it.put("status", "VERIFIED_COMPLETE_AT_STOP") }
            val repo = transport.repository(); repo.queue(f.id)
            assertThrows(IllegalStateException::class.java) { finish(repo, f.id) }
            assertFalse(transport.posts.any { it.operation == "VERIFY" })
            assertFalse(f.db.localE2aReceipts().queue(f.id)!!.autoRetry)
        }
    }

    @Test fun queueWriteFailureRollsBackVerificationAndReopensWithoutResendingReceipt() = runBlocking<Unit>(Dispatchers.IO) {
        frozen { f, transport ->
            val repo = transport.repository(); repo.queue(f.id)
            val requests = transport.all(); val first = requests.first()
            val draft = f.db.temporaryDrafts().get(f.id)
            val stop = f.db.localE2aStops().get(f.id)
            val audit = f.db.temporaryScans().events(f.id)
            f.db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER fail_e2a_queue BEFORE UPDATE ON local_e2a_queue
                WHEN NEW.state='READY' BEGIN SELECT RAISE(ABORT,'injected queue write failure'); END""")
            val failure = assertThrows(Exception::class.java) { repo.resume(f.id) }
            assertTrue(failure.message.orEmpty().contains("injected queue write failure"))
            val saved = f.db.localE2aReceipts().get(first.originalKey)!!
            assertEquals("RECEIVED", saved.state); assertNull(saved.signingKeyJson)
            assertArrayEquals(transport.received[first.originalKey], saved.responseBytes)
            assertEquals(draft, f.db.temporaryDrafts().get(f.id)); assertEquals(stop, f.db.localE2aStops().get(f.id))
            assertEquals(audit, f.db.temporaryScans().events(f.id)); assertEquals(requests, transport.all())
            assertEquals("ACTION_REQUIRED", f.db.localE2aReceipts().queue(f.id)?.state)
            f.db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_e2a_queue")
            f.reopen(); val resumed = transport.repository(); resumed.queue(f.id); finish(resumed, f.id)
            assertEquals("PARTIAL", f.db.localE2aReceipts().queue(f.id)?.state)
            assertEquals(1, transport.posts.count { it.originalKey == first.originalKey })
            assertArrayEquals(saved.responseBytes, f.db.localE2aReceipts().get(first.originalKey)?.responseBytes)
            assertEquals(draft, f.db.temporaryDrafts().get(f.id)); assertEquals(requests, transport.all())
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
