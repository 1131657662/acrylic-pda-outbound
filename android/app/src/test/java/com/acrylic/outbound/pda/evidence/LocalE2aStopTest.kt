package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.temporary.TemporaryFreeze
import com.acrylic.outbound.pda.temporary.TemporaryWorkFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
class LocalE2aStopTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun stoppedEventAndKeysSurviveRestartWithoutSecondStop() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { fixture ->
            fixture.start(); fixture.scan()
            val frozen = TemporaryFreeze(fixture.store).freeze(fixture.id, "短装待核对")
            val original = LocalE2aStopRepository(fixture.store).record(fixture.id)
            val event = checkNotNull(fixture.db.temporaryScans().event(original.stopEventId))
            assertEquals("STOP", event.type)
            assertEquals(frozen.id, event.detail)
            assertEquals(original.auditThroughSeq, event.localSeq)
            assertEquals(original, LocalE2aStopRepository(fixture.store).record(fixture.id))
            fixture.reopen()
            assertEquals(original, LocalE2aStopRepository(fixture.store).record(fixture.id))
            assertEquals(1, fixture.db.temporaryScans().events(fixture.id).count { it.type == "STOP" })
        }
    }

    @Test fun failedPrefixCheckRollsBackStopEventCounterAndBarrier() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { fixture ->
            fixture.start(); fixture.scan()
            val deviceId = fixture.catalog.request.deviceId
            fixture.db.openHelper.writableDatabase.execSQL(
                "UPDATE work_device SET nextCaptureSeq=nextCaptureSeq+1 WHERE deviceId=?", arrayOf(deviceId))
            val before = checkNotNull(fixture.db.work().device(deviceId))
            assertThrows(IllegalStateException::class.java) {
                TemporaryFreeze(fixture.store).freeze(fixture.id, "短装待核对")
            }
            assertEquals(before, fixture.db.work().device(deviceId))
            assertEquals("SCANNING", fixture.db.temporaryDrafts().get(fixture.id)?.state)
            assertNull(fixture.db.temporarySubmissions().forOrder(fixture.id))
            assertNull(fixture.db.localE2aStops().get(fixture.id))
            assertEquals(0, fixture.db.temporaryScans().events(fixture.id).count { it.type == "STOP" })
        }
    }

    @Test fun scanningDraftCannotCreateStopOrEvent() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { fixture ->
            fixture.start(); fixture.scan()
            assertThrows(IllegalStateException::class.java) {
                LocalE2aStopRepository(fixture.store).record(fixture.id)
            }
            assertNull(fixture.db.localE2aStops().get(fixture.id))
            assertEquals(0, fixture.db.temporaryScans().events(fixture.id).count { it.type == "STOP" })
        }
    }

    @Test fun lostBarrierCannotReissueStopEventOrOriginalKey() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { fixture ->
            fixture.start(); fixture.scan()
            TemporaryFreeze(fixture.store).freeze(fixture.id, "短装待核对")
            val original = LocalE2aStopRepository(fixture.store).record(fixture.id)
            removeTransfer(fixture)
            fixture.db.openHelper.writableDatabase.execSQL(
                "DELETE FROM local_e2a_object WHERE clientOrderId=?", arrayOf(fixture.id))
            fixture.db.openHelper.writableDatabase.execSQL(
                "DELETE FROM local_e2a_stop WHERE clientOrderId=?", arrayOf(fixture.id))
            assertThrows(IllegalStateException::class.java) {
                LocalE2aStopRepository(fixture.store).record(fixture.id)
            }
            assertEquals(1, fixture.db.temporaryScans().stopCount(fixture.id))
            assertEquals(original.stopEventId, fixture.db.temporaryScans().events(fixture.id)
                .single { it.type == "STOP" }.id)
        }
    }

    @Test fun legacyFrozenOrderWithoutOriginalStopCannotInventOne() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { fixture ->
            fixture.start(); fixture.scan()
            TemporaryFreeze(fixture.store).freeze(fixture.id, "短装待核对")
            removeTransfer(fixture)
            fixture.db.openHelper.writableDatabase.execSQL(
                "DELETE FROM local_e2a_object WHERE clientOrderId=?", arrayOf(fixture.id))
            fixture.db.openHelper.writableDatabase.execSQL(
                "DELETE FROM local_e2a_stop WHERE clientOrderId=?", arrayOf(fixture.id))
            fixture.db.openHelper.writableDatabase.execSQL(
                "DELETE FROM temporary_event WHERE clientOrderId=? AND type='STOP'", arrayOf(fixture.id))
            fixture.reopen()
            assertThrows(IllegalStateException::class.java) {
                LocalE2aStopRepository(fixture.store).record(fixture.id)
            }
            assertEquals(0, fixture.db.temporaryScans().stopCount(fixture.id))
            assertEquals("FROZEN", fixture.db.temporaryDrafts().get(fixture.id)?.state)
        }
    }

    private fun removeTransfer(fixture: TemporaryWorkFixture) {
        fixture.db.openHelper.writableDatabase.execSQL("DELETE FROM local_e2a_request WHERE clientOrderId=?", arrayOf(fixture.id))
        fixture.db.openHelper.writableDatabase.execSQL("DELETE FROM local_e2a_transfer WHERE clientOrderId=?", arrayOf(fixture.id))
    }
}
