package com.acrylic.outbound.pda.rescue

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
class RescueOrdersTest {
    @get:Rule val folder = TemporaryFolder()
    private fun scenario(block: (RescueOrdersFixture) -> Unit) = runBlocking<Unit>(Dispatchers.IO) {
        RescueOrdersFixture(folder.root).use(block)
    }

    @Test fun formalChoiceShowsBusinessDetailsAndBeijingSavedTime() = scenario { f ->
        val row = checkNotNull(f.db.preparations().order(f.work.assignment))
        f.db.openHelper.writableDatabase.execSQL("UPDATE prepared_order SET activatedAt=? WHERE assignmentId=?",
            arrayOf("2026-10-23T01:02:03Z", row.assignmentId))
        val label = RescueOrders(f.db).page(f.grant).rows.single().label
        val order = JSONObject(row.planJson).getJSONObject("order")
        assertTrue(label.contains("资料保存 2026-10-23 09:02:03"))
        assertTrue(label.contains("车号 ${order.getJSONObject("plan").getString("vehicleNo")}"))
        assertTrue(label.contains("订单号 ${order.getJSONObject("order").getString("orderNo")}"))
        assertEquals("记录编号 ${row.assignmentId}", label.lines().last())
    }

    @Test fun allSourcesShareNewestFirstOrderAcrossTimezones() = scenario { f ->
        val now = f.work.now
        f.draft("z-old", now.minusSeconds(10).toString())
        f.draft("a-new", now.plusSeconds(10).atOffset(java.time.ZoneOffset.ofHours(8)).toString())
        f.independent("ind-middle", now.plusSeconds(5))
        val rows = RescueOrders(f.db).page(f.grant).rows
        assertEquals(listOf("a-new", "ind-middle", f.work.assignment, "z-old"), rows.map { it.target.localOrderId })
        assertTrue(rows[1].label.contains("车号 合成独立车"))
        assertTrue(rows[1].label.contains("待主管对账"))
        assertTrue(rows[0].label.contains("订单号 合成单a-new"))
    }

    @Test fun stableTiesAndOlderRowsTraversePagesWithoutLossOrDuplicates() = scenario { f ->
        val now = f.work.now.plusSeconds(20)
        val ties = (0..42).map { "tie-%02d".format(it) }
        ties.reversed().forEach { f.draft(it, now.toString()) }
        f.draft("a-older", now.minusSeconds(1).toString())
        val actual = mutableListOf<String>(); val orders = RescueOrders(f.db)
        var cursor = ""; var pages = 0
        do {
            val page = orders.page(f.grant, cursor)
            actual += page.rows.map { it.target.localOrderId }
            cursor = page.nextCursor.orEmpty(); pages++
            check(pages <= 4)
        } while (cursor.isNotEmpty())
        assertEquals(ties + listOf("a-older", f.work.assignment), actual)
        assertEquals(actual.size, actual.toSet().size)
        assertEquals(3, pages)
        assertTrue(orders.find(f.grant, RescueTarget("TEMP", "tie-42"))!!.label.contains("合成单tie-42"))
    }

    @Test fun filteredPagesAndDirectLookupNeverExposeOutOfScopeDetails() = scenario { f ->
        (0..20).forEach { f.draft("restricted-$it", f.work.now.plusSeconds(1).toString()) }
        val access = RescueReadGrant(f.identity, setOf(f.work.assignment)) {}
        val orders = RescueOrders(f.db); val first = orders.page(access)
        assertTrue(first.rows.isEmpty()); assertNotNull(first.nextCursor)
        assertEquals(f.work.assignment, orders.page(access, first.nextCursor!!).rows.single().target.localOrderId)
        assertNull(orders.find(access, RescueTarget("TEMP", "restricted-0")))
        val foreign = RescueReadGrant(f.identity.copy(warehouseId = "foreign")) {}
        assertNull(orders.find(foreign, RescueTarget("FORMAL", f.work.assignment)))
    }

    @Test fun missingBusinessNumberAndInvalidDateHaveExplicitPlaceholders() = scenario { f ->
        val row = checkNotNull(f.db.preparations().order(f.work.assignment))
        val plan = JSONObject(row.planJson)
        plan.getJSONObject("order").getJSONObject("order").remove("orderNo")
        f.db.openHelper.writableDatabase.execSQL("UPDATE prepared_order SET planJson=?,activatedAt=? WHERE assignmentId=?",
            arrayOf(plan.toString(), "invalid-time", row.assignmentId))
        f.draft("new", f.work.now.toString(), "")
        val rows = RescueOrders(f.db).page(f.grant).rows
        assertEquals("new", rows.first().target.localOrderId)
        assertTrue(rows.first().label.contains("车号 未记录"))
        assertTrue(rows.last().label.contains("资料保存 暂无记录"))
        assertTrue(rows.last().label.contains("订单号 未记录"))
    }
}
