package com.acrylic.outbound.pda.temporary

import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.time.Instant
import java.util.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryLifecycleTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun nextSignedPreparationLeavesOldDraftSourceScansAndGuardsIntact() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val scan = f.scan(); val oldDraft = f.db.temporaryDrafts().get(f.id)
            val oldRequest = f.catalog.request; val oldParts = TemporaryParts(f.db).read(oldDraft!!.authorizationId, f.catalog.manifest)
            val next = NextConnection(f); val repository = TemporaryRepository(f.db, next, folder.newFolder())
            val prepared = repository.prepareNext(oldRequest.key) {}
            val nextRequest = repository.local()!!
            assertEquals(oldRequest.key, nextRequest.previousKey); assertNotEquals(oldRequest.key, nextRequest.key)
            assertNotEquals(oldDraft.authorizationId, prepared.authorizationId)
            assertEquals(oldDraft, f.db.temporaryDrafts().get(f.id)); assertEquals(oldRequest, f.db.temporary().request(oldRequest.key))
            oldParts.forEach { (part, bytes) -> assertArrayEquals(bytes, TemporaryParts(f.db).read(oldDraft.authorizationId, f.catalog.manifest).getValue(part)) }
            assertNotNull(f.db.temporaryScans().guard(scan.barcode))
            f.reopen(); assertEquals(oldDraft.authorizationId, f.store.check(f.id).authorizationId)
            val newDraft = TemporaryDraftRepository(TemporaryWorkStore(f.db, next, f.store.sample)).create(UUID.randomUUID().toString())
            assertEquals(prepared.authorizationId, newDraft.authorizationId)
        }
    }
    @Test fun lostNextIssueResponseReopensSameRequestWithoutForkingHistory() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            val old = f.catalog.request; val next = NextConnection(f); next.loseIssue = true
            val cache = folder.newFolder(); val repository = TemporaryRepository(f.db, next, cache)
            assertThrows(Exception::class.java) { repository.prepareNext(old.key) {} }
            val pending = repository.local()!!; assertEquals(old.key, pending.previousKey); assertNull(pending.receiptJson)
            assertThrows(Exception::class.java) { repository.prepareNext(old.key) {} }
            f.reopen(); next.loseIssue = false
            TemporaryRepository(f.db, next, cache).prepare {}
            assertEquals(listOf(pending.key, pending.key), next.issueKeys)
            assertEquals(pending.key, TemporaryRepository(f.db, next, cache).local()!!.key)
            assertNotNull(f.db.temporary().request(old.key))
        }
    }
    @Test fun unresolvedInputPreventsAnyNextPreparationNetworkRequest() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.receive(source = "MANUAL"); val next = NextConnection(f)
            assertThrows(Exception::class.java) { TemporaryRepository(f.db, next, folder.newFolder()).prepareNext(f.catalog.request.key) {} }
            assertEquals(0, next.calls); assertEquals(f.catalog.request, f.fixture.repository.local())
        }
    }
    @Test fun incompleteOriginalAcknowledgementCannotBeAbandonedForNewRequest() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.db.openHelper.writableDatabase.execSQL("UPDATE temporary_preparation SET ackJson=NULL")
            val next = NextConnection(f)
            assertThrows(Exception::class.java) { TemporaryRepository(f.db, next, folder.newFolder()).prepareNext(f.catalog.request.key) {} }
            assertEquals(0, next.calls)
        }
    }
    @Test fun failureToPersistSuccessorKeepsOriginalAsCurrent() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            val original = f.catalog.request; val next = NextConnection(f)
            f.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_successor BEFORE INSERT ON temporary_request WHEN NEW.previousKey IS NOT NULL BEGIN SELECT RAISE(ABORT,'injected request failure'); END")
            assertThrows(Exception::class.java) { TemporaryRepository(f.db, next, folder.newFolder()).prepareNext(original.key) {} }
            assertEquals(original, f.fixture.repository.local()); assertTrue(next.issueKeys.isEmpty())
        }
    }
    private class NextConnection(f: TemporaryWorkFixture) : TemporaryConnection by f.fixture {
        private val base = f.fixture
        private val fixture = base.fixture.getJSONObject("nextPreparation")
        val issueKeys = mutableListOf<String>()
        var calls = 0
        var loseIssue = false
        init { base.time = Instant.parse(fixture.getJSONObject("activationCommand").getString("activatedAt")) }
        override fun call(request: TemporaryConnection.Call): JSONObject {
            calls++
            return when {
                request.path == "pda/temporary-preparations" -> {
                    issueKeys.add(checkNotNull(request.key)); if (loseIssue) error("injected lost issue response")
                    fixture.getJSONObject("receipt")
                }
                request.path.endsWith("download-ack") -> fixture.getJSONObject("activationReceipt")
                else -> base.call(request)
            }
        }
        override fun download(request: TemporaryConnection.Download) {
            val part = request.path.substringAfterLast('/').substringBefore('?')
            request.target.writeBytes(Base64.getDecoder().decode(fixture.getJSONObject("gzip").getString(part)))
        }
    }
}
