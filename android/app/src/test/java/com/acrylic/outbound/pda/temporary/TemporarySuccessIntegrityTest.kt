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
class TemporarySuccessIntegrityTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun differentSignedHistoryCannotReplaceFirstAcceptance() = runBlocking<Unit>(Dispatchers.IO) {
        TemporarySuccessFixture(folder.root).use { f ->
            val hash = f.repository.retain(f.local.id, f.bytes); f.repository.verify(f.local.id, hash, f.key)
            val accepted = f.db.temporarySuccess().evidence(f.local.id)
            val conflict = JSONObject(f.bytes.toString(Charsets.UTF_8))
                .put("data", f.vector.getJSONObject("conflictingSuccessReceipt")).toString().toByteArray()
            val conflictHash = f.repository.retain(f.local.id, conflict)
            assertTrue(runCatching { f.repository.verify(f.local.id, conflictHash, f.key) }.isFailure)
            f.reopen(); assertEquals(accepted, f.db.temporarySuccess().evidence(f.local.id))
            assertArrayEquals(conflict, f.db.temporarySuccess().response(f.local.id, conflictHash)!!.responseBytes)
            assertNotNull(f.repository.read(f.local.id, f.key))
        }
    }

    @Test fun rawCorruptionIsDetectedOnHistoricalRead() = runBlocking<Unit>(Dispatchers.IO) {
        TemporarySuccessFixture(folder.root).use { f ->
            val hash = f.repository.retain(f.local.id, f.bytes); f.repository.verify(f.local.id, hash, f.key)
            f.db.openHelper.writableDatabase.execSQL("UPDATE temporary_success_response SET responseBytes=? WHERE submissionId=?",
                arrayOf("{}".toByteArray(), f.local.id))
            f.reopen(); assertTrue(runCatching { f.repository.read(f.local.id, f.key) }.isFailure)
        }
    }

    @Test fun acceptedKeyOrDigestCorruptionFailsClosed() = runBlocking<Unit>(Dispatchers.IO) {
        TemporarySuccessFixture(folder.root).use { f ->
            val hash = f.repository.retain(f.local.id, f.bytes); f.repository.verify(f.local.id, hash, f.key)
            val previous = f.db.temporarySuccess().evidence(f.local.id)!!
            f.db.openHelper.writableDatabase.execSQL("UPDATE temporary_success_evidence SET signingKeyJson='{}' WHERE submissionId=?", arrayOf(f.local.id))
            assertTrue(runCatching { f.repository.read(f.local.id, f.key) }.isFailure)
            f.db.openHelper.writableDatabase.execSQL("UPDATE temporary_success_evidence SET signingKeyJson=?,receiptSha256=? WHERE submissionId=?",
                arrayOf(previous.signingKeyJson, "0".repeat(64), f.local.id))
            assertTrue(runCatching { f.repository.read(f.local.id, f.key) }.isFailure)
        }
    }
}
