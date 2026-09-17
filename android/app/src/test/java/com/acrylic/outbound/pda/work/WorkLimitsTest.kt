package com.acrylic.outbound.pda.work

import com.acrylic.outbound.pda.data.PreparedBale
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class WorkLimitsTest {
    @get:Rule val folder = TemporaryFolder()
    private fun scenario(block: (WorkFixture) -> Unit) = runBlocking<Unit>(Dispatchers.IO) { WorkFixture(folder.root).use(block) }

    @Test fun productAndTotalLimitsAreCheckedBeforeEverySave() = scenario { f ->
        f.db.openHelper.writableDatabase.execSQL("UPDATE work_line SET plannedCount=1 WHERE deliveryCode='003'")
        f.scan(); val extra = extraBale(f, "003A0000001")
        val over = f.scan(extra.barcode)
        assertTrue(f.db.captures().get(over.id)!!.error.contains("产品已达"))
        f.processor.corrections.acknowledge(over.id)
        f.db.openHelper.writableDatabase.execSQL("UPDATE work_order SET plannedTotal=1")
        val total = f.scan(f.barcodes[1])
        assertTrue(f.db.captures().get(total.id)!!.error.contains("整单已达"))
        assertEquals(1, f.db.scans().total(f.assignment).count)
    }

    @Test fun unknownProductAndUnavailableBaleCannotBeSaved() = scenario { f ->
        val extra = extraBale(f, "999A0000001")
        val wrong = f.scan(extra.barcode)
        assertTrue(f.db.captures().get(wrong.id)!!.error.contains("产品不属于"))
        f.processor.corrections.acknowledge(wrong.id)
        f.db.openHelper.writableDatabase.execSQL("UPDATE prepared_bale SET available=0 WHERE barcode=?", arrayOf(f.barcodes[0]))
        val unavailable = f.scan()
        assertTrue(f.db.captures().get(unavailable.id)!!.error.contains("不可用"))
        assertEquals(0, f.db.scans().total(f.assignment).count)
    }

    @Test fun tinyWeightIsAcceptedExactlyAndNoOneHundredSixteenBaleLimitExists() = scenario { f ->
        repeat(130) { index ->
            val row = extraBale(f, "003A" + index.toString().padStart(7, '0'))
            f.scan(row.barcode)
        }
        assertEquals(130, f.db.scans().total(f.assignment).count)
        assertEquals(130, f.db.scans().total(f.assignment).grams)
        assertEquals((1L..130L).toList(), f.db.scans().page(f.assignment, 0, 200).map { it.orderScanSeq })
        f.store.verifyTotals(f.assignment)
    }

    @Test fun aLocalGuardRemainsBlockingEvenIfNewPreparedStateClaimsNewerCycle() = scenario { f ->
        val original = f.scan()
        f.db.openHelper.writableDatabase.execSQL("UPDATE prepared_bale SET dispatchCycle=2")
        val second = f.scan()
        assertEquals(2L, second.context.expectedDispatchCycle)
        assertTrue(f.db.captures().get(second.id)!!.error.contains("本机订单"))
        assertEquals(original.id, f.db.scans().guard(f.barcodes[0])!!.captureId)
    }

    private fun extraBale(f: WorkFixture, barcode: String): PreparedBale {
        val row = PreparedBale(f.assignment, barcode, UUID.randomUUID().toString(), barcode.take(3), "model", "name", "0.001", 1, 1, true)
        f.db.preparations().insertBales(listOf(row)); return row
    }
}
