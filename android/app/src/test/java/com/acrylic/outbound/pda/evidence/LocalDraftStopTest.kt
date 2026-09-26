package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.temporary.TemporaryFreeze
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LocalDraftStopTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun emptyDraftPreservesIncompletePlanWithoutInventingSubmission() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            val original = f.db.temporaryDrafts().get(f.id)!!
            val row = LocalDraftStopRepository(f.store).stop(f.id)
            val meta = JSONObject(row.metadataJson)
            assertEquals("DRAFT", meta.getJSONObject("draft").getString("state"))
            assertEquals("0", meta.getString("captureHighWatermark"))
            assertEquals(original.copy(state = "EVIDENCE_STOPPED"), f.db.temporaryDrafts().get(f.id))
            assertNull(f.db.temporarySubmissions().forOrder(f.id)); assertNull(f.db.localE2aStops().get(f.id))
            assertEquals(0, f.db.temporaryScans().stopCount(f.id))
            f.reopen()
            assertEquals(row, LocalDraftStopRepository(f.store).read(f.id))
            assertEquals(row, LocalDraftStopRepository(f.store).stop(f.id))
            assertEquals(1, f.db.temporaryScans().events(f.id).count { it.type == "LOCAL_EVIDENCE_STOP" })
        }
    }

    @Test fun zeroPackageScanningOrderStopsAfterAuthorizationExpires() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.fixture.time = f.fixture.time.plusSeconds(7 * 86400)
            val row = LocalDraftStopRepository(f.store).stop(f.id)
            assertEquals("SCANNING", JSONObject(row.metadataJson).getJSONObject("draft").getString("state"))
            assertEquals(0L, f.db.temporaryScans().total(f.id).count)
            assertNull(f.db.temporarySubmissions().forOrder(f.id))
        }
    }

    @Test fun receivedInputIsNeverValidatedOrDiscardedByStopping() = pending("RECEIVED")
    @Test fun manualConfirmationRemainsPending() = pending("WAITING_CONFIRM")
    @Test fun durableConfirmationIntentIsNeverSavedByStopping() = pending("CONFIRM_REQUESTED")

    private fun pending(state: String) = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val input = f.receive(source = "MANUAL")
            if (state != "RECEIVED") f.processor.reception.validate(input.id)
            if (state == "CONFIRM_REQUESTED") f.processor.saving.confirm(input.id)
            val original = f.db.temporaryScans().capture(input.id)!!
            assertEquals(state, original.state)
            val stop = LocalDraftStopRepository(f.store).stop(f.id)
            f.reopen()
            assertEquals(stop, LocalDraftStopRepository(f.store).read(f.id))
            assertEquals(original, f.db.temporaryScans().capture(input.id))
            assertNull(f.db.temporaryScans().scan(input.id)); assertNull(f.db.temporarySubmissions().forOrder(f.id))
            if (state != "WAITING_CONFIRM") assertThrows(IllegalStateException::class.java) { f.processor.drain(f.id) }
            assertThrows(IllegalStateException::class.java) { f.processor.saving.confirm(input.id) }
            assertThrows(IllegalStateException::class.java) { f.processor.corrections.discard(input.id, "不得自动处置") }
        }
    }

    @Test fun unacknowledgedInvalidInputRemainsUnacknowledged() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val input = f.receive("bad", "MANUAL"); f.processor.drain(f.id)
            val original = f.db.temporaryScans().capture(input.id)!!
            assertEquals("INVALID", original.state); assertNull(original.failureAckAt)
            LocalDraftStopRepository(f.store).stop(f.id); f.reopen()
            LocalDraftStopRepository(f.store).read(f.id)
            assertEquals(original, f.db.temporaryScans().capture(input.id))
            assertThrows(IllegalStateException::class.java) { f.processor.corrections.acknowledge(input.id) }
        }
    }

    @Test fun committedGuardCannotBeReleasedOrOrderRestarted() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val capture = f.scan(); val guard = f.db.temporaryScans().guard(capture.barcode)
            val stop = LocalDraftStopRepository(f.store).stop(f.id); f.reopen()
            assertEquals(stop, LocalDraftStopRepository(f.store).read(f.id))
            assertEquals(guard, f.db.temporaryScans().guard(capture.barcode))
            assertThrows(IllegalStateException::class.java) { f.receive() }
            assertThrows(IllegalStateException::class.java) { f.processor.corrections.voidLast(f.id, capture.id, "卸车") }
            assertThrows(IllegalStateException::class.java) { f.drafts.start(f.id, 1) }
            assertThrows(IllegalStateException::class.java) { TemporaryFreeze(f.store).freeze(f.id, "") }
            assertNull(f.db.temporarySubmissions().forOrder(f.id))
        }
    }

    @Test fun failedPrefixRollsBackEventCounterSnapshotAndDraftState() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.receive()
            f.db.openHelper.writableDatabase.execSQL("UPDATE work_device SET nextCaptureSeq=nextCaptureSeq+1")
            val before = f.db.work().device(f.catalog.request.deviceId)
            val events = f.db.temporaryScans().events(f.id)
            assertThrows(IllegalStateException::class.java) { LocalDraftStopRepository(f.store).stop(f.id) }
            assertEquals(before, f.db.work().device(f.catalog.request.deviceId))
            assertEquals(events, f.db.temporaryScans().events(f.id))
            assertEquals("SCANNING", f.db.temporaryDrafts().get(f.id)?.state)
            assertNull(f.db.localDraftStops().get(f.id)); assertEquals(0L, f.db.localDraftStops().count(f.id))
        }
    }

    @Test fun frozenSubmissionCannotAcquireAnUnsubmittedStop() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.scan(); val frozen = TemporaryFreeze(f.store).freeze(f.id, "短装")
            val stop = f.db.localE2aStops().get(f.id)
            assertThrows(IllegalStateException::class.java) { LocalDraftStopRepository(f.store).stop(f.id) }
            assertEquals(frozen, f.db.temporarySubmissions().forOrder(f.id)); assertEquals(stop, f.db.localE2aStops().get(f.id))
            assertNull(f.db.localDraftStops().get(f.id))
        }
    }
}
