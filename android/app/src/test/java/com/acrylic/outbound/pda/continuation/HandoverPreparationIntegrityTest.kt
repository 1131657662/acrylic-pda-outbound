package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.preparation.LocalActivation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class HandoverPreparationIntegrityTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun extraInventoryWithMatchingLocalRowCountBlocksFirstImport() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverImportFixture(folder.root).use { f ->
            LocalActivation(f.db).activate(f.input)
            val extra = insertExtraInventory(f)

            val failure = assertThrows(IllegalStateException::class.java) { f.install() }

            assertEquals("本机固定库存行数与签名资料不一致", failure.message)
            assertNull(f.db.work().order(f.target))
            assertEquals("STAGED", f.db.handoverStages().get(f.caseId)?.state)
            assertEquals(extra, f.db.preparations().bale(f.target, extra.barcode))
        }
    }

    @Test fun extraInventoryWithMatchingLocalRowCountBlocksImportRetry() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverImportFixture(folder.root).use { f ->
            val original = f.install()
            val extra = insertExtraInventory(f)
            f.reopen()

            val failure = assertThrows(IllegalStateException::class.java) { f.install() }

            assertEquals("本机固定库存行数与签名资料不一致", failure.message)
            assertEquals(original, f.db.work().order(f.target))
            assertEquals("IMPORTED_FROZEN", f.db.handoverStages().get(f.caseId)?.state)
            assertEquals(extra, f.db.preparations().bale(f.target, extra.barcode))
        }
    }

    private fun insertExtraInventory(f: HandoverImportFixture): com.acrylic.outbound.pda.data.PreparedBale {
        val barcode = f.body.getJSONArray("scans").getJSONObject(0).getString("barcode")
        val original = checkNotNull(f.db.preparations().bale(f.target, barcode))
        val extra = original.copy(barcode = "10099999999", baleId = UUID.randomUUID().toString())
        f.db.runInTransaction {
            f.db.preparations().insertBales(listOf(extra))
            f.db.openHelper.writableDatabase.execSQL(
                "UPDATE prepared_order SET rowCount=rowCount+1 WHERE assignmentId=?", arrayOf(f.target))
        }
        assertEquals(f.db.preparations().count(f.target), f.db.preparations().order(f.target)?.rowCount)
        return extra
    }
}
