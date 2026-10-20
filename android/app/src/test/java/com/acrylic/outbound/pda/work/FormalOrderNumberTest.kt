package com.acrylic.outbound.pda.work

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.ui.WorkDetailsText
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
class FormalOrderNumberTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun originalNumberSurvivesRoomReopenWithoutWritingRecords() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            val expected = JSONObject(f.store.prepared(f.assignment).planJson)
                .getJSONObject("order").getJSONObject("order").getString("orderNo")
            val before = f.db.work().events(f.assignment)
            fun page() = WorkDetailsReader(f.db, f.assignment) { f.access.identity(f.store.prepared(f.assignment)) }
                .page(WorkDetailFilter.ALL)
            assertEquals(expected, page().orderNo)
            f.reload()
            val restored = page()
            assertEquals(expected, restored.orderNo)
            assertTrue(WorkDetailsText.summary(restored).contains("订单号 $expected"))
            assertEquals(before, f.db.work().events(f.assignment))
            assertTrue(restored.rows.isEmpty()); assertEquals(0L, restored.order.actualCount)
        }
    }

    @Test fun missingOriginalNumberIsExplicitAndNeverBecomesVehicleOrInternalId() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            val prepared = f.store.prepared(f.assignment); val work = f.db.work().order(f.assignment)!!
            val plan = JSONObject(prepared.planJson)
            plan.getJSONObject("order").getJSONObject("order").remove("orderNo")
            val text = plan.toString(); val assignment = JSONObject(prepared.assignmentJson)
                .put("planSha256", JwtVerifier.sha(text.toByteArray(Charsets.UTF_8)))
            val number = FormalOrderNumber.read(prepared.copy(planJson = text, assignmentJson = assignment.toString()), work)
            assertNull(number)
            val page = WorkDetailsPage(work, emptyList(), emptyList(), false, false, number)
            assertTrue(WorkDetailsText.summary(page).contains("订单号缺失"))
            assertFalse(WorkDetailsText.summary(page).contains("订单号 " + work.orderId))
        }
    }

    @Test fun mismatchedPlanOrOrderIsRejectedRatherThanDisplayingForeignNumber() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            val prepared = f.store.prepared(f.assignment); val work = f.db.work().order(f.assignment)!!
            assertThrows(Exception::class.java) { FormalOrderNumber.read(prepared.copy(planJson = "{}"), work) }
            assertThrows(Exception::class.java) { FormalOrderNumber.read(prepared, work.copy(orderId = "foreign")) }
        }
    }
}
