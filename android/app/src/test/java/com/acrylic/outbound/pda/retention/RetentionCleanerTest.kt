package com.acrylic.outbound.pda.retention

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.data.*
import com.acrylic.outbound.pda.update.UpdateGate
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.time.Duration

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RetentionCleanerTest {
    private lateinit var f: RetentionFixture
    @Before fun setup() {
        f = RetentionFixture(Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PdaDatabase::class.java)
            .allowMainThreadQueries().build())
    }
    @After fun close() { f.db.close(); UpdateGate.endInstall() }

    @Test fun onlyRetiredCompleteBaselinesOlderThanNinetyDaysAreRemoved() {
        val oldOrder = f.order(); val oldBarcode = f.barcode()
        val currentOrder = f.order(baseline = "current"); val currentBarcode = f.barcode(baseline = "current")
        f.retire()
        assertEquals(RetentionResult(1, 1), f.cleaner.clean())
        assertFalse(f.has(oldOrder)); assertFalse(f.has(oldBarcode))
        assertTrue(f.has(currentOrder)); assertTrue(f.has(currentBarcode))
        assertEquals(RetentionResult(), f.cleaner.clean())
    }

    @Test fun businessUpdateAgeDoesNotBackdateLocalRetentionAndBoundaryIsStrict() {
        val row = f.order(); f.retire(); f.now = f.now.minusMillis(1)
        assertEquals(RetentionResult(), f.cleaner.clean()); assertTrue(f.has(row))
        f.now = f.now.plusMillis(1)
        assertEquals(1, f.cleaner.clean().orders)
    }

    @Test fun orphanedLegacyProjectionWithoutObservedSuccessIsPreserved() {
        val order = f.order(baseline = "unobserved"); val barcode = f.barcode(baseline = "unobserved")
        f.retire(); assertEquals(RetentionResult(), f.cleaner.clean())
        assertTrue(f.has(order)); assertTrue(f.has(barcode))
    }

    @Test fun absentInvalidOrIncompleteSuccessStateCannotAuthorizeCleaning() {
        val order = f.order(); val barcode = f.barcode(); f.retire()
        for (stream in listOf("ORDER", "BARCODE")) {
            val good = f.state(stream, "current")
            val blocked = listOf(good.copy(lastSuccessAt = null), good.copy(lastSuccessAt = "bad"),
                good.copy(cursor = null), good.copy(activeBaselineId = null), good.copy(downloadId = "next"),
                good.copy(downloadCursor = "next-page"), good.copy(downloadWatermark = 50))
            blocked.forEach { state ->
                f.db.sync().saveState(state)
                f.db.sync().saveState(f.state(if (stream == "ORDER") "BARCODE" else "ORDER", "current").copy(cursor = null))
                assertEquals(RetentionResult(), f.cleaner.clean())
            }
        }
        assertTrue(f.has(order)); assertTrue(f.has(barcode))
    }

    @Test fun downloadInProgressProtectsBothActiveAndDownloadingBaselines() {
        val old = f.order(); val downloading = f.order(baseline = "next")
        f.retire()
        val state = f.state("ORDER", "current").copy(cursor = null, downloadId = "old", downloadCursor = "page")
        f.db.sync().saveState(state)
        assertEquals(RetentionResult(), f.cleaner.clean())
        assertTrue(f.has(old)); assertTrue(f.has(downloading))
    }

    @Test fun reactivatedBaselineStartsANewRetirementPeriod() {
        val old = f.barcode(); f.retire(); f.state("BARCODE", "old")
        f.cleaner.clean(); assertTrue(f.has(old))
        f.state("BARCODE", "current"); f.cleaner.clean(); assertTrue(f.has(old))
        f.now = f.now.plus(Duration.ofDays(90)).plusMillis(1)
        assertEquals(1, f.cleaner.clean().barcodes)
    }

    @Test fun recoveryAndUnknownEpochPreserveEvenExpiredCaches() {
        val row = f.order(); f.retire()
        for (mode in listOf("RECONCILING", "RECOVERING", "unknown")) {
            f.db.sync().saveRecovery(LocalRecoveryControl(observedServerEpoch = "1", mode = mode))
            assertTrue(f.cleaner.clean().deferred)
        }
        f.db.sync().saveRecovery(LocalRecoveryControl(observedServerEpoch = "2", mode = "NORMAL"))
        assertTrue(f.cleaner.clean().deferred); assertTrue(f.has(row))
    }

    @Test fun sameBaselineIdsInOtherNamespacesAreNotDeleted() {
        val otherWarehouse = f.order(warehouse = "other"); val otherEpoch = f.barcode(epoch = "2")
        f.order(); f.barcode(); f.retire()
        assertEquals(RetentionResult(1, 1), f.cleaner.clean())
        assertTrue(f.has(otherWarehouse)); assertTrue(f.has(otherEpoch))
    }

    @Test fun pendingClaimKeepsItsProjection() {
        val row = f.order()
        f.db.preparations().insertIntent(ClaimIntent(row.orderId, "u", "d", "key", "body", "1"))
        f.retire(); assertEquals(RetentionResult(), f.cleaner.clean()); assertTrue(f.has(row))
    }

    @Test fun cleanupIsBoundedAndContinuesOnTheNextRefresh() {
        repeat(501) { f.order("order-$it"); f.barcode(it.toString().padStart(11, '0')) }
        f.retire()
        assertEquals(RetentionResult(500, 500), f.cleaner.clean())
        assertEquals(RetentionResult(1, 1), f.cleaner.clean())
    }

    @Test fun partialDeleteFailureRollsBackBothProjectionTables() {
        val order = f.order(); val barcode = f.barcode(); f.retire()
        // ORDER sorts after BARCODE, so failure in ORDER must undo the earlier barcode deletion.
        f.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_clean BEFORE DELETE ON local_cloud_order BEGIN SELECT RAISE(ABORT, 'disk full'); END")
        assertThrows(Exception::class.java) { f.cleaner.clean() }
        assertTrue(f.has(order)); assertTrue(f.has(barcode))
        f.db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_clean")
        assertEquals(RetentionResult(1, 1), f.cleaner.clean())
    }

    @Test fun activeBusinessOrInstallerDefersCleaningWithoutTouchingCaches() {
        val row = f.order(); f.retire()
        UpdateGate.enterBusiness().use { assertTrue(f.cleaner.clean().deferred); assertTrue(f.has(row)) }
        UpdateGate.beginInstall()
        assertTrue(f.cleaner.clean().deferred); assertTrue(f.has(row))
        UpdateGate.endInstall(); assertEquals(1, f.cleaner.clean().orders)
    }

    @Test fun unavailableTrustedTimeDoesNotRemoveAnything() {
        val row = f.order(); f.retire()
        val cleaner = RetentionCleaner(f.db, { "1" }) { error("clock changed") }
        assertThrows(IllegalStateException::class.java) { cleaner.clean() }
        assertTrue(f.has(row)); assertEquals(1, f.cleaner.clean().orders)
    }
}
