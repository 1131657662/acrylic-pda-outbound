package com.acrylic.outbound.pda.temporary

import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.json.JSONObject
import java.time.Instant

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryDraftCancellationTest {
    @get:Rule val folder = TemporaryFolder()
    private fun input(f: TemporaryWorkFixture) = TemporaryCancelInput(checkNotNull(f.db.temporaryDrafts().get(f.id)).revision, "车辆取消，未装车")
    private fun cancel(f: TemporaryWorkFixture, value: TemporaryCancelInput = input(f)) = TemporaryDraftCancellation(f.store).cancel(f.id, value)
    @Test fun savedPlanSurvivesCancellationAndOriginalRetryAfterReopen() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            val row = f.drafts.save(f.id, f.plan()); val lines = f.db.temporaryDrafts().lines(f.id)
            val before = f.db.temporaryScans().events(f.id); val request = input(f)
            val result = cancel(f, request)
            assertEquals(row.copy(state = "CANCELLED"), result)
            f.reopen(); assertEquals(result, cancel(f, request))
            assertEquals(lines, f.db.temporaryDrafts().lines(f.id))
            val events = f.db.temporaryScans().events(f.id)
            assertEquals(before, events.dropLast(1)); assertEquals("TEMP_DRAFT_CANCELLED", events.last().type)
            assertFalse(JSONObject(events.last().detail).getBoolean("serverCancellation"))
            assertNull(f.db.temporarySubmissions().forOrder(f.id))
            assertThrows(Exception::class.java) { cancel(f, request.copy(reason = "换理由")) }
            assertThrows(Exception::class.java) { f.drafts.save(f.id, f.plan()) }
            assertThrows(Exception::class.java) { f.drafts.start(f.id, row.revision) }
        }
    }
    @Test fun emptyNewDraftCanBeCancelledWithoutManufacturingPlan() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            val row = checkNotNull(f.db.temporaryDrafts().get(f.id))
            assertEquals(row.copy(state = "CANCELLED"), cancel(f))
            assertTrue(f.db.temporaryDrafts().lines(f.id).isEmpty())
        }
    }
    @Test fun allVoidedHistoryIsRetainedOnZeroWorkCancellation() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val capture = f.scan(); f.processor.corrections.voidSelected(f.id, capture.id, "确认未装车")
            val scans = f.db.temporaryScans().allScans(f.id); val captures = f.db.temporaryScans().captures(f.id)
            assertEquals("CANCELLED", cancel(f).state)
            assertEquals(scans, f.db.temporaryScans().allScans(f.id)); assertEquals(captures, f.db.temporaryScans().captures(f.id))
            assertEquals("VOID", scans.single().status); assertEquals(0L, f.db.temporaryScans().guardCount(f.id))
        }
    }
    @Test fun expiryDoesNotForceNetworkOrExtendScanningQualification() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.fixture.time = Instant.parse(f.catalog.manifest.getString("expiresAt")).plusSeconds(1)
            val calls = f.fixture.calls.toList()
            assertEquals("CANCELLED", cancel(f).state); assertEquals(calls, f.fixture.calls)
            assertThrows(Exception::class.java) { f.receive() }
        }
    }
    @Test fun activeScanCannotBeCancelledOrReleased() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val capture = f.scan(); val events = f.db.temporaryScans().events(f.id)
            assertThrows(Exception::class.java) { cancel(f) }
            assertEquals("SCANNING", f.db.temporaryDrafts().get(f.id)?.state)
            assertEquals(capture.id, f.db.temporaryScans().guard(capture.barcode)?.captureId)
            assertEquals(events, f.db.temporaryScans().events(f.id))
        }
    }
    @Test fun orphanedOwnerCannotBeHiddenByZeroActiveCount() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val capture = f.scan()
            f.db.temporaryScans().markVoid(capture.id)
            assertEquals(0L, f.db.temporaryScans().total(f.id).count)
            assertThrows(Exception::class.java) { cancel(f) }
            assertEquals(capture.id, f.db.temporaryScans().guard(capture.barcode)?.captureId)
        }
    }
    @Test fun pendingAndUnacknowledgedFailureMustBeProcessed() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val capture = f.receive(source = "MANUAL")
            assertThrows(Exception::class.java) { cancel(f) }
            f.db.temporaryScans().updateCapture(capture.copy(state = "INVALID", error = "合成错误"))
            assertThrows(Exception::class.java) { cancel(f) }
            f.processor.corrections.acknowledge(capture.id)
            assertEquals("CANCELLED", cancel(f).state)
            assertNotNull(f.db.temporaryScans().capture(capture.id)?.failureAckAt)
        }
    }
    @Test fun staleRevisionAndInvalidReasonCannotWriteAudit() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            val request = input(f); f.drafts.save(f.id, f.plan())
            val before = f.db.temporaryScans().events(f.id)
            for (value in listOf(request, input(f).copy(reason = " "), input(f).copy(reason = "a\nb"), input(f).copy(reason = "a".repeat(501))))
                assertThrows(Exception::class.java) { cancel(f, value) }
            assertEquals(before, f.db.temporaryScans().events(f.id))
        }
    }
    @Test fun changedIdentityAndRevokedCapabilityDoNotCancelOriginal() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            val original = f.fixture.auth!!.toString()
            f.fixture.auth!!.put("userId", java.util.UUID.randomUUID().toString())
            assertThrows(Exception::class.java) { cancel(f) }
            f.fixture.auth = JSONObject(original).put("blockedReason", "已撤权")
            assertThrows(Exception::class.java) { cancel(f) }
            assertEquals("DRAFT", f.db.temporaryDrafts().get(f.id)?.state)
        }
    }
    @Test fun frozenAndContinuationStatesCannotUseOrdinaryCancellation() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            val original = checkNotNull(f.db.temporaryDrafts().get(f.id))
            for (state in listOf("FROZEN", "EVIDENCE_STOPPED", "CONTINUATION_BLOCKED", "HANDOVER_BLOCKED")) {
                f.db.temporaryDrafts().update(original.copy(state = state))
                assertThrows(Exception::class.java) { cancel(f) }
            }
            f.db.temporaryDrafts().update(original.copy(workRound = 2))
            assertThrows(Exception::class.java) { cancel(f) }
            assertTrue(f.db.temporaryScans().events(f.id).none { it.type == "TEMP_DRAFT_CANCELLED" })
        }
    }
    @Test fun finalAuditSqlFailureRollsBackStateAndDeviceSequence() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            val row = f.db.temporaryDrafts().get(f.id); val device = f.db.work().device(row!!.creatorDeviceId)
            val events = f.db.temporaryScans().events(f.id)
            f.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER cancel_tail BEFORE UPDATE ON work_device BEGIN SELECT RAISE(ABORT,'cancel tail failure'); END")
            assertThrows(Exception::class.java) { cancel(f) }
            assertEquals(row, f.db.temporaryDrafts().get(f.id)); assertEquals(device, f.db.work().device(row.creatorDeviceId))
            assertEquals(events, f.db.temporaryScans().events(f.id))
            f.db.openHelper.writableDatabase.execSQL("DROP TRIGGER cancel_tail")
            assertEquals("CANCELLED", cancel(f).state)
        }
    }
    @Test fun concurrentOriginalCancellationCreatesExactlyOneAudit() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            val request = input(f)
            val results = (1..2).map { async { TemporaryDraftCancellation(f.newStore()).cancel(f.id, request) } }.awaitAll()
            assertEquals(results.first(), results.last())
            assertEquals(1, f.db.temporaryScans().events(f.id).count { it.type == "TEMP_DRAFT_CANCELLED" })
        }
    }
    @Test fun corruptedCancellationReceiptIsNotDisplayedAsVerified() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            cancel(f)
            f.db.openHelper.writableDatabase.execSQL("UPDATE temporary_event SET detail='{}' WHERE type='TEMP_DRAFT_CANCELLED'")
            assertThrows(Exception::class.java) { TemporaryDraftCancellation(f.store).read(f.id) }
            assertEquals("CANCELLED", f.db.temporaryDrafts().get(f.id)?.state)
        }
    }
}
