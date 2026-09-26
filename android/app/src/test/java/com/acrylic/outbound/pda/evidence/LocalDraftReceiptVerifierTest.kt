package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.temporary.TemporaryWorkFixture
import com.acrylic.outbound.pda.submission.SubmissionJson
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LocalDraftReceiptVerifierTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun signedWrongSourcesAndCapabilitiesAreRejected() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            LocalDraftStopRepository(f.store).stop(f.id); val wire = LocalDraftRecoveryFixture(f); wire.repository().prepare(f.id)
            val facts = LocalDraftReceiptVerifier.Expected(wire.stop(), wire.all().first(), wire.counts())
            listOf("originalKey", "evidenceId", "clientOrderId", "warehouseId", "creatorDeviceId", "originalOperatorId",
                "rootSha256", "requestSha256", "operationKind", "status", "businessFactsStatus").forEach { field ->
                wire.signer.change = { it.put(field, "wrong") }; rejects(facts, wire)
            }
            listOf("localDeletionAllowed", "releaseAllowed", "mayResumeScanning", "writeConfirmationAvailable").forEach { field ->
                wire.signer.change = { it.put(field, true) }; rejects(facts, wire)
            }
            wire.signer.change = { it.put("unknownCapability", true) }; rejects(facts, wire)
            wire.signer.change = { it.put("receiptVersion", "1") }; rejects(facts, wire)
        }
    }
    @Test fun unknownKeyOrModifiedRawResponseCannotBecomeTrusted() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            LocalDraftStopRepository(f.store).stop(f.id); val wire = LocalDraftRecoveryFixture(f); wire.repository().prepare(f.id)
            val facts = LocalDraftReceiptVerifier.Expected(wire.stop(), wire.all().first(), wire.counts())
            val raw = wire.signer.response(facts)
            assertThrows(Exception::class.java) { LocalDraftReceiptVerifier.verify(facts, raw, LocalDraftTestReceipts().key) }
            val changed = JSONObject(raw.toString(Charsets.UTF_8)).put("recordedAt", "2026-09-24T01:00:00Z")
            assertThrows(IllegalStateException::class.java) { LocalDraftReceiptVerifier.verify(facts,
                SubmissionJson.canonical(changed).toByteArray(), wire.signer.key) }
            assertThrows(IllegalStateException::class.java) { LocalDraftReceiptVerifier.verify(facts, raw + byteArrayOf(32), wire.signer.key) }
        }
    }
    @Test fun signedIncorrectItemAndVerifyCountsFailClosed() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.scan(); LocalDraftStopRepository(f.store).stop(f.id)
            val wire = LocalDraftRecoveryFixture(f); wire.repository().prepare(f.id)
            val item = LocalDraftReceiptVerifier.Expected(wire.stop(), wire.all()[1], wire.counts())
            wire.signer.change = { it.getJSONObject("detail").put("ordinal", 99) }; rejects(item, wire)
            wire.signer.change = { it.getJSONObject("detail").put("bytesSha256", "0".repeat(64)) }; rejects(item, wire)
            val verify = LocalDraftReceiptVerifier.Expected(wire.stop(), wire.all().last(), wire.counts())
            listOf("recordCount", "byteCount", "ownCaptureCount", "otherCaptureCount", "archiveCount").forEach { field ->
                wire.signer.change = { it.getJSONObject("detail").put(field, 999) }; rejects(verify, wire)
            }
        }
    }
    @Test fun verifiedStoredResponseIsRecheckedAfterReopen() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            LocalDraftStopRepository(f.store).stop(f.id); val wire = LocalDraftRecoveryFixture(f); wire.repository().prepare(f.id)
            wire.repository().resume(f.id); val key = wire.all().first().originalKey
            f.db.openHelper.writableDatabase.execSQL("UPDATE local_draft_receipt SET responseBytes=? WHERE originalKey=?", arrayOf(byteArrayOf(123, 125), key))
            val posts = wire.posts.size; f.reopen()
            assertThrows(Exception::class.java) { wire.repository().resume(f.id) }; assertEquals(posts, wire.posts.size)
            assertArrayEquals(byteArrayOf(123, 125), f.db.localDraftTransfers().receipt(key)!!.responseBytes)
        }
    }
    private fun rejects(facts: LocalDraftReceiptVerifier.Expected, wire: LocalDraftRecoveryFixture) {
        assertThrows(IllegalStateException::class.java) { LocalDraftReceiptVerifier.verify(facts, wire.signer.response(facts), wire.signer.key) }
    }
}
