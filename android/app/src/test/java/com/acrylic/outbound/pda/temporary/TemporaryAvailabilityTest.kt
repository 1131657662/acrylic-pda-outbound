package com.acrylic.outbound.pda.temporary

import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryAvailabilityTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun newerCloudBlockBetweenValidationAndConfirmRejectsOriginalCapture() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val row = f.receive(source = "MANUAL"); f.processor.drain(f.id)
            f.cloud(status = "QUARANTINED"); f.processor.saving.confirm(row.id); f.processor.drain(f.id)
            assertEquals("BUSINESS_REJECTED", f.db.temporaryScans().capture(row.id)?.state)
            assertNull(f.db.temporaryScans().scan(row.id)); assertTrue(f.db.temporaryScans().capture(row.id)!!.error.contains("隔离"))
        }
    }
    @Test fun oldProjectionCannotOverrideNewerSignedBaseline() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.cloud(status = "DISPATCHED", version = 0)
            val row = f.scan(); assertNotNull(f.db.temporaryScans().scan(row.id))
        }
    }
    @Test fun newCycleCannotRefreshCaptureOrReleaseGuard() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val first = f.scan(); val guard = f.db.temporaryScans().guard(first.barcode)
            f.cloud(version = 2, cycle = 2); val repeated = f.scan()
            assertNull(f.db.temporaryScans().scan(repeated.id)); assertEquals(1L, repeated.expectedDispatchCycle)
            assertEquals(guard, f.db.temporaryScans().guard(first.barcode))
        }
    }
    @Test fun sameVersionConflictingStatusFailsClosed() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.cloud(status = "RESERVED", version = 1); val row = f.scan()
            assertNull(f.db.temporaryScans().scan(row.id)); assertTrue(f.db.temporaryScans().capture(row.id)!!.error.contains("同版本"))
        }
    }
    @Test fun productAtPlanLimitRejectsAdditionalAvailableBale() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start("1"); val first = f.scan()
            // Remove only the test guard to reach the independent quantity rule with the two-row signed fixture.
            f.db.temporaryScans().release(first.id)
            val repeated = f.scan()
            assertNull(f.db.temporaryScans().scan(repeated.id)); assertTrue(f.db.temporaryScans().capture(repeated.id)!!.error.contains("计划"))
            assertEquals(1L, f.db.temporaryScans().total(f.id).count)
        }
    }
}
