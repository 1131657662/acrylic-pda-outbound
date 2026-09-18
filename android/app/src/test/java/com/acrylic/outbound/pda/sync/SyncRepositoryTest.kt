package com.acrylic.outbound.pda.sync

import com.acrylic.outbound.pda.network.ApiFailure
import com.acrylic.outbound.pda.work.WorkFixture
import kotlinx.coroutines.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.IOException
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class SyncRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private fun auth() = JSONObject().put("serverRecoveryEpoch", "1").put("warehouseId", "warehouse")
    private fun baseline(id: String, complete: Boolean) = JSONObject().put("streamType", "ORDER").put("recoveryEpoch", "1")
        .put("warehouseId", "warehouse").put("baselineId", id).put("baselineNextCursor", if (complete) JSONObject.NULL else "page+/=")
        .put("baselineComplete", complete).put("highWatermark", "2").put("nextCursor", if (complete) "cursor+/=" else JSONObject.NULL)
        .put("hasMore", true).put("baseline", JSONArray()).put("events", JSONArray())
    private fun delta() = baseline("b", true).put("baselineId", JSONObject.NULL).put("hasMore", false)
    private fun row() = JSONObject().put("orderId", UUID.randomUUID().toString()).put("status", "ASSIGNED")
        .put("rowVersion", "2").put("updatedAt", "2026-09-18T00:00:00Z")

    @Test fun interruptionResumesSameBaselineWithEncodedCursorThenRequestsDelta() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            var calls = 0
            val first = SyncRepository(f.db, ::auth) { _, _ -> if (calls++ == 0) baseline("b1", false) else throw IOException("断网") }
            assertThrows(IOException::class.java) { first.refreshOrders() }
            assertNull(f.db.sync().state("1", "warehouse")!!.cursor); f.reload()
            val paths = mutableListOf<String>()
            val resumed = SyncRepository(f.db, ::auth) { path, epoch ->
                paths.add(path); assertEquals("1", epoch)
                if (paths.size == 1) baseline("b1", true) else delta()
            }
            assertEquals("订单状态已同步", resumed.refreshOrders())
            assertEquals(listOf("pda/sync/orders?limit=50&baselineId=b1&baselineCursor=page%2B%2F%3D",
                "pda/sync/orders?limit=50&cursor=cursor%2B%2F%3D"), paths)
            assertEquals("b1", f.db.sync().state("1", "warehouse")!!.activeBaselineId)
        }
    }
    @Test fun expiredStagingIsReplacedWithoutDeletingWorkFacts() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            f.scan(); val facts = f.db.scans().page(f.assignment, 0, 10)
            var calls = 0; val staged = row()
            val first = SyncRepository(f.db, ::auth) { _, _ ->
                if (calls++ > 0) throw IOException("断网")
                baseline("expired", false).also { it.getJSONArray("baseline").put(staged) }
            }
            assertThrows(IOException::class.java) { first.refreshOrders() }
            calls = 0
            val next = SyncRepository(f.db, ::auth) { path, _ -> when (calls++) {
                0 -> { assertTrue(path.contains("baselineId=expired")); throw ApiFailure("SYNC_BASELINE_EXPIRED", "到期") }
                1 -> { assertEquals("pda/sync/orders?limit=50", path); baseline("fresh", true) }
                else -> delta()
            } }
            next.refreshOrders()
            assertNull(f.db.sync().order("1", "warehouse", "expired", staged.getString("orderId")))
            assertEquals("fresh", f.db.sync().state("1", "warehouse")!!.activeBaselineId)
            assertEquals(facts, f.db.scans().page(f.assignment, 0, 10)); assertNotNull(f.db.scans().guard(f.barcodes[0]))
        }
    }
    @Test fun only404AllowsLegacyQueryModeAndOtherFailuresKeepCheckpoint() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            val old = SyncRepository(f.db, ::auth) { _, _ -> throw ApiFailure("NOT_FOUND", "旧后台", httpStatus = 404) }
            assertTrue(old.refreshOrders().contains("尚未提供订单同步")); assertNull(f.db.sync().state("1", "warehouse"))
            val denied = SyncRepository(f.db, ::auth) { _, _ -> throw ApiFailure("PERMISSION_DENIED", "权限失效", httpStatus = 403) }
            assertThrows(ApiFailure::class.java) { denied.refreshOrders() }
            assertNull(f.db.sync().state("1", "warehouse"))
        }
    }
}
