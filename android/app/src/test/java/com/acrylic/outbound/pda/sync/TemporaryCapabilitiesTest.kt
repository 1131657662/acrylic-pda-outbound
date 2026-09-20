package com.acrylic.outbound.pda.sync

import com.acrylic.outbound.pda.network.PdaCapabilities
import com.acrylic.outbound.pda.work.WorkFixture
import kotlinx.coroutines.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryCapabilitiesTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun temporaryOnlyOperatorSkipsFormalSyncWhileBarcodesContinueInForegroundAndBackground() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            f.session.put("capabilities", JSONObject().put("formalOrders", false).put("temporaryWork", true).put("barcodeSync", true).put("auditUpload", true))
            f.scan(); val original = f.db.scans().page(f.assignment, 0, 10); var requests = 0
            val orders = SyncRepository(f.db, { f.session }) { _, _ -> error("Formal orders must not be requested") }
            val barcodes = BarcodeRepository(f.db, { f.session }) { _, _ ->
                requests++
                JSONObject().put("streamType", "BARCODE").put("warehouseId", f.session.getString("warehouseId")).put("recoveryEpoch", "1")
                    .put("baselineId", "base").put("baselineComplete", true).put("baselineNextCursor", JSONObject.NULL)
                    .put("highWatermark", "0").put("nextCursor", "cursor").put("hasMore", false).put("baseline", JSONArray()).put("events", JSONArray())
            }
            assertFalse(orders.syncOrders().supported); assertFalse(orders.syncOrders(run = SyncRun(1, true)).supported)
            assertTrue(barcodes.syncBarcodes().supported); assertEquals(1, requests)
            assertEquals(original, f.db.scans().page(f.assignment, 0, 10)); assertNotNull(f.db.scans().guard(f.barcodes[0]))
            assertFalse(f.session.has("blockedReason"))
        }
    }
    @Test fun oldSessionKeepsFormalCompatibilityButNeverEnablesTemporaryPreparation() {
        assertTrue(PdaCapabilities.formal(JSONObject())); assertFalse(PdaCapabilities.temporary(JSONObject()))
        val malformed = JSONObject().put("capabilities", JSONObject().put("temporaryWork", "true"))
        assertFalse(PdaCapabilities.temporary(malformed)); assertFalse(PdaCapabilities.formal(malformed))
    }
}
