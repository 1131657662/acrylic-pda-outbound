package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.LocalRecoveryControl
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
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
class TemporaryFreezeTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun freezeReopensSameIdentityAndImmutableEvidenceWithoutReleasingGuard() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start("1"); f.barcodes.forEach { f.scan(it) }; val original = TemporaryFreeze(f.store).freeze(f.id, "")
            val body = JSONObject(original.payload)
            assertTrue(body.isNull("assignmentId")); assertEquals("TEMP", body.getJSONObject("source").getString("kind"))
            assertEquals(f.catalog.preparation.authorizationId, body.getString("authorizationId"))
            assertEquals(original.sha256, JwtVerifier.sha(original.payload.toByteArray()))
            assertEquals(f.db.temporaryDrafts().get(f.id)?.planSha256, JwtVerifier.sha(SubmissionJson.canonical(body.getJSONObject("temporaryOrder")).toByteArray()))
            f.reopen(); assertEquals(original, TemporaryFreeze(f.store).freeze(f.id, "重试不能改正文"))
            assertNotNull(f.db.temporaryScans().guard(f.barcodes[0]))
            assertTrue(runCatching { f.receive() }.isFailure)
            assertTrue(runCatching { f.processor.corrections.voidLast(f.id, body.getJSONArray("scans").getJSONObject(1).getString("captureId"), "冻结后") }.isFailure)
        }
    }
    @Test fun pendingInputAndShortReasonBlockWithoutPartialFreeze() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.scan(); val pending = f.receive(f.barcodes[1], "MANUAL"); f.processor.drain(f.id)
            assertTrue(runCatching { TemporaryFreeze(f.store).freeze(f.id, "短装") }.isFailure)
            f.processor.corrections.discard(pending.id, "尚未装车")
            assertTrue(runCatching { TemporaryFreeze(f.store).freeze(f.id, "") }.isFailure)
            assertNull(f.db.temporarySubmissions().forOrder(f.id)); assertEquals("SCANNING", f.db.temporaryDrafts().get(f.id)?.state)
            assertEquals("FROZEN", TemporaryFreeze(f.store).freeze(f.id, "实物短装待核对").state)
        }
    }
    @Test fun storageFailureRollsBackFreezeEventAndSequence() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.scan(); val events = f.db.temporaryScans().events(f.id); val device = f.db.work().device(f.catalog.request.deviceId)
            f.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_freeze BEFORE INSERT ON temporary_submission BEGIN SELECT RAISE(ABORT,'injected storage failure'); END")
            assertTrue(runCatching { TemporaryFreeze(f.store).freeze(f.id, "短装") }.isFailure)
            assertEquals(events, f.db.temporaryScans().events(f.id)); assertEquals(device, f.db.work().device(f.catalog.request.deviceId))
            assertEquals("SCANNING", f.db.temporaryDrafts().get(f.id)?.state)
            f.db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_freeze")
            assertNotNull(TemporaryFreeze(f.store).freeze(f.id, "短装"))
        }
    }
    @Test fun expiredQualificationPreservesEvidenceButRecoveryBarrierBlocksFreeze() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.scan(); f.fixture.time = f.fixture.time.plusSeconds(200000)
            f.db.sync().saveRecovery(LocalRecoveryControl(observedServerEpoch = "2", mode = "RECONCILING"))
            assertTrue(runCatching { TemporaryFreeze(f.store).freeze(f.id, "短装") }.isFailure)
            f.db.sync().saveRecovery(LocalRecoveryControl(observedServerEpoch = "1", mode = "NORMAL"))
            assertEquals("FROZEN", TemporaryFreeze(f.store).freeze(f.id, "短装").state)
        }
    }
}
