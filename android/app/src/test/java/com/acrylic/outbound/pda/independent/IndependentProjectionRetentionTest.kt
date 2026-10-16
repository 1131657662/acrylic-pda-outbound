package com.acrylic.outbound.pda.independent

import com.acrylic.outbound.pda.retention.RetentionFixture
import com.acrylic.outbound.pda.retention.RetentionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class IndependentProjectionRetentionTest {
    @Test fun pendingIndependentCaptureRetainsItsOldProjection() = scenario("pending") { f ->
        f.reception.receive(f.input(source = "MANUAL"))
    }

    @Test fun savedIndependentGuardRetainsItsOldProjection() = scenario("saved") { f ->
        f.save()
    }

    @Test fun voidedIndependentScanStillRetainsItsHistoryProjection() = scenario("voided") { f ->
        val capture = f.save()
        f.corrections.voidSelected(f.command.id, capture.capture.id, "合成实物卸回核验")
        assertNull(f.store.dao.guard("003A0000001"))
    }

    private fun scenario(label: String, prepare: (IndependentScanFixture) -> Unit) = runBlocking<Unit>(Dispatchers.IO) {
        IndependentScanFixture().use { f ->
            f.start(); prepare(f)
            val fixture = RetentionFixture(f.task.sku.db)
            val protected = fixture.barcode("003A0000001")
            val disposable = fixture.barcode("009A0000009")
            fixture.retire()
            val original = f.store.dao.captures(f.command.id)
            retain(f, "$label-before")
            try {
                assertEquals(RetentionResult(barcodes = 1), fixture.cleaner.clean())
                assertTrue(fixture.has(protected)); assertFalse(fixture.has(disposable))
                assertEquals(original, f.store.dao.captures(f.command.id))
                f.reopen()
                assertTrue(RetentionFixture(f.task.sku.db).has(protected))
                assertEquals(original, f.store.dao.captures(f.command.id))
            } finally { retain(f, "$label-after") }
        }
    }

    private fun retain(f: IndependentScanFixture, name: String) {
        val directory = System.getenv("PDA_RETENTION_TEST_OUTPUT")?.let(::File)?.apply { mkdirs() } ?: return
        val target = File(directory, "$name.db")
        check(!target.exists())
        f.task.sku.db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use {
            assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
        }
        f.task.sku.context.getDatabasePath("outbound-pda.db").copyTo(target)
    }
}
