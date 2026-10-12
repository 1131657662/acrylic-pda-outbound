package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.rescue.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryCancellationSnapshotTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun cancelledSourceExportsOriginalVoidAndAuditWithoutSubmission() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val capture = f.scan(); f.processor.corrections.voidSelected(f.id, capture.id, "确认未装车")
            val row = checkNotNull(f.db.temporaryDrafts().get(f.id))
            val cancelled = TemporaryDraftCancellation(f.store).cancel(f.id, TemporaryCancelInput(row.revision, "车辆取消"))
            val grant = RescueReadGrant(RescueReadIdentity(row.creatorDeviceId, row.operatorId, row.warehouseId)) {}
            val snapshot = RescueRoomSnapshot(f.db).read(RescueTarget("TEMP", f.id), grant)
            assertEquals("CANCELLED", snapshot.order.getString("state"))
            assertEquals("VOID", snapshot.scans.single().getString("status"))
            assertEquals(capture.id, snapshot.captures.single().getString("id"))
            assertEquals("TEMP_DRAFT_CANCELLED", snapshot.events.last().getString("type"))
            assertFalse(snapshot.originals.containsKey("submission.json"))
            assertEquals("0", RescueFacts.summary(snapshot).getString("active"))
            assertEquals(cancelled, f.db.temporaryDrafts().get(f.id))
            val event = f.db.temporaryScans().events(f.id).last()
            val audit = TemporaryAuditRecord(event, row.creatorDeviceId, row.authorizationId, row.operatorId).json()
            assertEquals(event.detail, audit.getString("detail")); assertEquals(f.id, audit.getJSONObject("source").getString("clientOrderId"))
        }
    }
}
