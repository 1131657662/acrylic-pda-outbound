package com.acrylic.outbound.pda.temporary

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
class TemporarySuccessStorageTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun rawBeforeVerificationSurvivesReopenWithoutChangingFrozenWork() = runBlocking<Unit>(Dispatchers.IO) {
        TemporarySuccessFixture(folder.root).use { f ->
            val frozen = f.db.temporarySubmissions().forOrder(f.work.id)
            val draft = f.db.temporaryDrafts().get(f.work.id)
            val hash = f.repository.retain(f.local.id, f.bytes)
            f.reopen(); assertNull(f.repository.read(f.local.id, f.key))
            assertArrayEquals(f.bytes, f.db.temporarySuccess().response(f.local.id, hash)!!.responseBytes)
            assertEquals("SUCCEEDED", f.repository.verify(f.local.id, hash, f.key).getString("status"))
            f.reopen(); assertNotNull(f.repository.read(f.local.id, f.key))
            assertEquals(f.local, f.db.temporarySubmissions().get(f.local.id))
            assertEquals(frozen, f.db.temporarySubmissions().forOrder(f.work.id))
            assertEquals(draft, f.db.temporaryDrafts().get(f.work.id))
            assertNotNull(f.db.temporaryScans().guard(f.work.barcodes[0]))
        }
    }

    @Test fun malformedOriginalIsRetainedAndCannotReplaceAcceptedEvidence() = runBlocking<Unit>(Dispatchers.IO) {
        TemporarySuccessFixture(folder.root).use { f ->
            val bad = "{broken".toByteArray(); val badHash = f.repository.retain(f.local.id, bad)
            assertTrue(runCatching { f.repository.verify(f.local.id, badHash, f.key) }.isFailure)
            f.reopen(); assertArrayEquals(bad, f.db.temporarySuccess().response(f.local.id, badHash)!!.responseBytes)
            val hash = f.repository.retain(f.local.id, f.bytes); f.repository.verify(f.local.id, hash, f.key)
            val accepted = f.db.temporarySuccess().evidence(f.local.id)
            assertTrue(runCatching { f.repository.verify(f.local.id, hash, JSONObject(f.key.toString()).put("keyId", "unknown")) }.isFailure)
            assertEquals("VERIFIED", f.db.temporarySuccess().response(f.local.id, hash)!!.state)
            assertEquals(accepted, f.db.temporarySuccess().evidence(f.local.id))
        }
    }

    @Test fun transactionFailureRollsBackAcceptanceWhileOriginalRemainsRetryable() = runBlocking<Unit>(Dispatchers.IO) {
        TemporarySuccessFixture(folder.root).use { f ->
            val hash = f.repository.retain(f.local.id, f.bytes)
            f.db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER reject_success BEFORE UPDATE ON temporary_success_response
                WHEN NEW.state='VERIFIED' BEGIN SELECT RAISE(ABORT,'injected final write failure'); END""")
            assertTrue(runCatching { f.repository.verify(f.local.id, hash, f.key) }.isFailure)
            assertNull(f.db.temporarySuccess().evidence(f.local.id))
            assertArrayEquals(f.bytes, f.db.temporarySuccess().response(f.local.id, hash)!!.responseBytes)
            f.db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_success")
            f.reopen(); f.repository.verify(f.local.id, hash, f.key)
            assertNotNull(f.repository.read(f.local.id, f.key))
        }
    }

    @Test fun differentEnvelopesOfSameReceiptRetainFirstAcceptanceAndBothOriginals() = runBlocking<Unit>(Dispatchers.IO) {
        TemporarySuccessFixture(folder.root).use { f ->
            val hash = f.repository.retain(f.local.id, f.bytes); f.repository.verify(f.local.id, hash, f.key)
            val another = JSONObject(f.bytes.toString(Charsets.UTF_8)).put("requestId", "another-read").toString().toByteArray()
            val otherHash = f.repository.retain(f.local.id, another); f.repository.verify(f.local.id, otherHash, f.key)
            assertEquals(hash, f.db.temporarySuccess().evidence(f.local.id)!!.responseSha256)
            assertArrayEquals(another, f.db.temporarySuccess().response(f.local.id, otherHash)!!.responseBytes)
        }
    }
}
