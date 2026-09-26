package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.network.ApiFailure
import com.acrylic.outbound.pda.security.JwtVerifier
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryProductSuccessTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun querySuccessRetainsOriginalAndStopsRetryWithoutRemovingGuards() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryProductSuccessFixture(folder.root).use { t ->
            assertEquals("SYNCED", t.repository.upload(t.id).state)
            val saved = t.db.temporarySubmissions().get(t.id)!!
            assertFalse(saved.autoRetry); assertEquals(t.f.local.abortReason, saved.abortReason)
            assertEquals(t.f.local.payload, saved.payload); assertEquals(0, t.db.temporarySubmissions().unknownCount())
            assertArrayEquals(t.f.bytes, t.db.temporarySuccess().response(t.id, JwtVerifier.sha(t.f.bytes))!!.responseBytes)
            t.reopen(); assertEquals("SYNCED", t.repository.checked(t.id).state)
            assertEquals("SYNCED", t.repository.upload(t.id).state); assertEquals(1, t.requests.size)
            assertNotNull(t.db.temporaryScans().guard(t.guardBarcode))
            assertEquals("FROZEN", t.db.temporaryDrafts().get(t.f.local.clientOrderId)!!.state)
            assertNull(t.db.temporaryArchives().request(t.id, "1"))
        }
    }

    @Test fun missingQueryThenSuccessfulOriginalPostUsesFrozenIdentity() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryProductSuccessFixture(folder.root).use { t ->
            t.noAbort(); t.reply = { if (it.body == null) throw ApiFailure("SUBMISSION_NOT_FOUND", "missing") else t.f.bytes }
            assertEquals("SYNCED", t.repository.upload(t.id).state)
            assertEquals("pda/temporary-orders/submissions", t.requests.last().path)
            assertArrayEquals(t.f.local.payload.toByteArray(), t.requests.last().body)
        }
    }

    @Test fun lateAbortSuccessKeepsOriginalIntentAfterLostAcknowledgement() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryProductSuccessFixture(folder.root).use { t ->
            var lost = true
            t.reply = { request ->
                if (request.body == null) t.pending()
                else { if (lost) { lost = false; throw java.io.IOException("lost terminal response") }; t.f.bytes }
            }
            assertTrue(runCatching { t.repository.upload(t.id) }.isFailure)
            t.reopen(); assertEquals("SYNCED", t.repository.upload(t.id).state)
            val aborts = t.requests.filter { it.path.endsWith("/abort") }; assertEquals(2, aborts.size)
            assertArrayEquals(aborts[0].body, aborts[1].body)
            assertEquals(t.f.local.abortReason, t.db.temporarySubmissions().get(t.id)!!.abortReason)
        }
    }

    @Test fun malformedAndUnsignedSuccessRemainRawAndNeverBecomeSynced() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryProductSuccessFixture(folder.root).use { t ->
            val bad = JSONObject(t.f.bytes.toString(Charsets.UTF_8)).apply { getJSONObject("data").put("stockReleased", true) }.toString().toByteArray()
            for (raw in listOf("{broken".toByteArray(), bad)) {
                t.reply = { raw }; assertTrue(runCatching { t.repository.upload(t.id) }.isFailure)
                assertArrayEquals(raw, t.db.temporarySuccess().response(t.id, JwtVerifier.sha(raw))!!.responseBytes)
                assertEquals("ACTION_REQUIRED", t.db.temporarySubmissions().get(t.id)!!.state)
                assertNull(t.db.temporarySuccess().evidence(t.id))
            }
        }
    }

    @Test fun recoveryEnvelopeRetainsRawAndDurablyBlocksProductAcceptance() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryProductSuccessFixture(folder.root).use { t ->
            val raw = JSONObject(t.f.bytes.toString(Charsets.UTF_8)).put("serverRecoveryEpoch", "2").toString().toByteArray()
            t.reply = { raw }; assertTrue(runCatching { t.repository.upload(t.id) }.isFailure)
            assertEquals("RECONCILING", t.db.sync().recovery()!!.mode)
            assertNull(t.db.temporarySuccess().evidence(t.id)); assertArrayEquals(raw, t.db.temporarySuccess().response(t.id, JwtVerifier.sha(raw))!!.responseBytes)
            t.reopen(); assertTrue(runCatching { t.repository.upload(t.id) }.isFailure); assertEquals(1, t.requests.size)
        }
    }

    @Test fun delayedNewSessionCannotAcceptSuccess() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryProductSuccessFixture(folder.root).use { t ->
            t.reply = { t.f.work.fixture.auth!!.put("localSessionId", "new-login"); t.f.bytes }
            assertTrue(runCatching { t.repository.upload(t.id) }.isFailure)
            assertNull(t.db.temporarySuccess().evidence(t.id)); assertEquals("ACTION_REQUIRED", t.db.temporarySubmissions().get(t.id)!!.state)
        }
    }

    @Test fun syncedLabelWithoutOriginalCannotDisplayVerifiedSuccess() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryProductSuccessFixture(folder.root).use { t ->
            t.db.temporarySubmissions().progress(t.id, "SYNCED", t.f.local.receipt, "", false)
            assertEquals("ACTION_REQUIRED", t.repository.checked(t.id).state)
            assertTrue(t.requests.isEmpty()); assertNull(t.db.temporarySuccess().evidence(t.id))
        }
    }

    @Test fun productCommitFailureRollsBackAcceptanceThenRecoversOriginal() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryProductSuccessFixture(folder.root).use { t ->
            t.db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER reject_product_success BEFORE UPDATE ON temporary_submission
                WHEN NEW.state='SYNCED' BEGIN SELECT RAISE(ABORT,'injected product commit failure'); END""")
            assertTrue(runCatching { t.repository.upload(t.id) }.isFailure)
            assertNull(t.db.temporarySuccess().evidence(t.id)); assertArrayEquals(t.f.bytes, t.db.temporarySuccess().response(t.id, JwtVerifier.sha(t.f.bytes))!!.responseBytes)
            t.db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_product_success")
            t.reopen(); assertEquals("SYNCED", t.repository.upload(t.id).state)
            t.db.openHelper.writableDatabase.execSQL("UPDATE temporary_success_response SET responseBytes=? WHERE submissionId=?", arrayOf("bad".toByteArray(), t.id))
            t.reopen(); assertEquals("ACTION_REQUIRED", t.repository.checked(t.id).state)
        }
    }
}
